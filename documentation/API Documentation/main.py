from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
from typing import Optional, List
import requests
import polyline
import math
import psycopg2

GOOGLE_API_KEY = "GOOGLE_API_KEY_GOES_HERE"
DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json"
NEARBY_RADIUS_METERS = 100

DB_CONFIG = {
    "host": "localhost",
    "dbname": "sensenav",
    "user": "postgres",
    "password": "369_28",
}

SENSITIVITY_COLORS = {"Low": "#3B8BD4", "Medium": "#EF9F27", "High": "#E24B4A"}

app = FastAPI(title="SenseNav Route Scoring API")


# ---------- Shared helpers ----------

def haversine_meters(lat1, lon1, lat2, lon2):
    R = 6371000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi/2)**2 + math.cos(phi1)*math.cos(phi2)*math.sin(dlambda/2)**2
    return 2 * R * math.asin(math.sqrt(a))


def get_db_connection():
    try:
        return psycopg2.connect(**DB_CONFIG)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database connection failed: {e}")


# ---------- /route models and logic ----------

class Coordinates(BaseModel):
    lat: float
    lng: float


class RouteRequest(BaseModel):
    origin: Coordinates
    destination: Coordinates


class ScoredRoute(BaseModel):
    summary: str
    polyline: str
    distance_text: str
    duration_text: str
    sensitivity: Optional[str]
    color: Optional[str]
    avg_pedestrian_count: Optional[float]


class RouteResponse(BaseModel):
    mode: str
    routes: List[ScoredRoute]


def get_all_sensors(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT location_id, latitude, longitude FROM sensor_location;")
        return [{"location_id": r[0], "latitude": r[1], "longitude": r[2]} for r in cur.fetchall()]


def get_sensor_averages(conn):
    query = """
        WITH ranked AS (
            SELECT location_id, total_of_directions,
                   ROW_NUMBER() OVER (PARTITION BY location_id ORDER BY sensing_datetime DESC) AS rn
            FROM pedestrian_minute_count
        )
        SELECT location_id, AVG(total_of_directions) AS avg_count
        FROM ranked WHERE rn <= 5
        GROUP BY location_id;
    """
    with conn.cursor() as cur:
        cur.execute(query)
        return {row[0]: float(row[1]) for row in cur.fetchall()}


def get_google_routes(origin, destination, mode="walking"):
    params = {
        "origin": f"{origin.lat},{origin.lng}",
        "destination": f"{destination.lat},{destination.lng}",
        "alternatives": "true",
        "mode": mode,
        "key": GOOGLE_API_KEY,
    }
    resp = requests.get(DIRECTIONS_URL, params=params, timeout=30)
    resp.raise_for_status()
    data = resp.json()
    if data['status'] != 'OK':
        raise HTTPException(status_code=502, detail=f"Directions API error: {data['status']}")
    return data['routes']


def score_route(route_points, sensors, sensor_averages):
    nearby_counts = []
    for point in route_points:
        for sensor in sensors:
            if sensor['location_id'] not in sensor_averages:
                continue
            dist = haversine_meters(point[0], point[1], sensor['latitude'], sensor['longitude'])
            if dist <= NEARBY_RADIUS_METERS:
                nearby_counts.append(sensor_averages[sensor['location_id']])

    if not nearby_counts:
        return None, None

    avg_score = sum(nearby_counts) / len(nearby_counts)
    if avg_score < 30:
        return "Low", avg_score
    elif avg_score < 100:
        return "Medium", avg_score
    else:
        return "High", avg_score


@app.get("/")
def root():
    return {"status": "SenseNav API is running"}


@app.post("/route", response_model=RouteResponse)
def get_scored_routes(req: RouteRequest):
    conn = get_db_connection()
    try:
        sensors = get_all_sensors(conn)
        sensor_averages = get_sensor_averages(conn)
    finally:
        conn.close()

    raw_routes = get_google_routes(req.origin, req.destination)

    scored_routes = []
    for route in raw_routes:
        encoded = route['overview_polyline']['points']
        decoded_points = polyline.decode(encoded)
        sensitivity, avg_count = score_route(decoded_points, sensors, sensor_averages)

        scored_routes.append(ScoredRoute(
            summary=route.get('summary', ''),
            polyline=encoded,
            distance_text=route['legs'][0]['distance']['text'],
            duration_text=route['legs'][0]['duration']['text'],
            sensitivity=sensitivity,
            color=SENSITIVITY_COLORS.get(sensitivity) if sensitivity else None,
            avg_pedestrian_count=avg_count,
        ))

    any_covered = any(r.sensitivity is not None for r in scored_routes)
    mode = "scored" if any_covered else "unscored"
    if mode == "unscored":
        scored_routes = scored_routes[:1]

    return RouteResponse(mode=mode, routes=scored_routes)


# ---------- /landmarks/nearby models and logic ----------

class NearbyLandmark(BaseModel):
    landmark_id: int
    feature_name: str
    theme: str
    sub_theme: str
    sensory_rating: str
    latitude: float
    longitude: float
    distance_km: float


class LandmarksResponse(BaseModel):
    landmarks: List[NearbyLandmark]


def get_all_landmarks(conn):
    with conn.cursor() as cur:
        cur.execute("""
            SELECT landmark_id, feature_name, theme, sub_theme, sensory_rating, latitude, longitude
            FROM landmark;
        """)
        return [
            {
                "landmark_id": r[0],
                "feature_name": r[1],
                "theme": r[2],
                "sub_theme": r[3],
                "sensory_rating": r[4],
                "latitude": r[5],
                "longitude": r[6],
            }
            for r in cur.fetchall()
        ]


@app.get("/landmarks/nearby", response_model=LandmarksResponse)
def get_nearby_landmarks(
    lat: float = Query(..., description="User's current latitude"),
    lng: float = Query(..., description="User's current longitude"),
    radius_km: float = Query(10, description="Search radius in kilometers"),
    sensory_rating: Optional[str] = Query(None, description="Filter by 'Calm', 'Neutral', or 'Busy'. Omit for all."),
    limit: int = Query(10, description="Max number of results to return"),
):
    conn = get_db_connection()
    try:
        landmarks = get_all_landmarks(conn)
    finally:
        conn.close()

    results = []
    for lm in landmarks:
        dist_km = haversine_meters(lat, lng, lm['latitude'], lm['longitude']) / 1000
        if dist_km > radius_km:
            continue
        if sensory_rating and lm['sensory_rating'] != sensory_rating:
            continue
        results.append({**lm, "distance_km": round(dist_km, 2)})

    results.sort(key=lambda r: r['distance_km'])
    results = results[:limit]

    return LandmarksResponse(landmarks=[NearbyLandmark(**r) for r in results])
