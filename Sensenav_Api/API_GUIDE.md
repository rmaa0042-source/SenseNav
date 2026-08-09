# SenseNav API — Frontend Integration Guide

Two endpoints are live, both from the same running server. This doc
covers what to send and what you get back for each.

---

## Base URL

```
http://34.172.95.142:8000
```

**Heads up:** this IP is not fixed — it can change if the VM restarts.
If requests suddenly stop working, check with Elijah for the current IP
before assuming your code is broken.

## Try it without writing any code first

```
http://34.172.95.142:8000/docs
```

Interactive test page — expand an endpoint, click "Try it out", edit the
fields, click "Execute", see a real response. Good for understanding the
data shape before writing Flutter code.

---

## 1. `POST /route` — route sensitivity scoring

Give it a start and end point, get back 2-3 walking routes color-coded
by how busy the nearby streets currently are.

**Request:**
```json
POST /route
Content-Type: application/json

{
  "origin": { "lat": -37.8136, "lng": 144.9631 },
  "destination": { "lat": -37.8226, "lng": 144.9548 }
}
```

**Response:**
```json
{
  "mode": "scored",
  "routes": [
    {
      "summary": "Elizabeth St and Flinders St",
      "polyline": "bnxeF}axsZfBm@N@DEHOh@Of@S...",
      "distance_text": "1.8 km",
      "duration_text": "26 mins",
      "sensitivity": "Medium",
      "color": "#EF9F27",
      "avg_pedestrian_count": 37.39
    },
    {
      "summary": "Collins St",
      "polyline": "bnxeF}axsZfBm@N@DEHOh@Of@S...",
      "distance_text": "1.7 km",
      "duration_text": "26 mins",
      "sensitivity": "Low",
      "color": "#3B8BD4",
      "avg_pedestrian_count": 27.05
    }
  ]
}
```

**Key fields:**
- `mode`: `"scored"` (multiple color-coded routes, render normally) or
  `"unscored"` (no sensor coverage in this area — only **one** plain
  route is returned, `sensitivity`/`color` are `null`; show it like a
  regular Google Maps route with no coloring)
- `polyline`: standard Google-encoded polyline, decode with any map
  SDK's built-in decoder
- `sensitivity`: `"Low"` / `"Medium"` / `"High"` / `null`
- `color`: hex string ready to use directly as the line color, or `null`

**Sensitivity thresholds** (subject to tuning later): avg pedestrian
count below 30 = Low, 30-99 = Medium, 100+ = High.

---

## 2. `GET /landmarks/nearby` — nearby landmarks

Give it a location, get back nearby landmarks (parks, churches,
galleries, transport, etc.) each tagged Calm / Neutral / Busy.

**Request:** query parameters, no body.

```
GET /landmarks/nearby?lat=-37.8136&lng=144.9631
```

All parameters:

| Param | Required | Default | Notes |
|---|---|---|---|
| `lat` | Yes | — | User's current latitude |
| `lng` | Yes | — | User's current longitude |
| `radius_km` | No | `10` | Search radius |
| `sensory_rating` | No | *(all)* | `Calm`, `Neutral`, or `Busy` — omit for a mix |
| `limit` | No | `10` | Max results. If fewer exist within radius, you just get fewer — no error. |

Example with all params:
```
GET /landmarks/nearby?lat=-37.8136&lng=144.9631&radius_km=5&sensory_rating=Calm&limit=20
```

**Response:**
```json
{
  "landmarks": [
    {
      "landmark_id": 168,
      "feature_name": "Myer",
      "theme": "Retail",
      "sub_theme": "Department Store",
      "sensory_rating": "Busy",
      "latitude": -37.8135911985281,
      "longitude": 144.963855087868,
      "distance_km": 0.07
    },
    {
      "landmark_id": 202,
      "feature_name": "St Francis Church",
      "theme": "Place of Worship",
      "sub_theme": "Church",
      "sensory_rating": "Calm",
      "latitude": -37.8118847831837,
      "longitude": 144.962422614541,
      "distance_km": 0.2
    }
  ]
}
```

Results are always sorted closest-first. `sensory_rating` is a
rule-based estimate from the landmark's category (not measured
sensor data) — worth keeping that in mind if it's surfaced in the UI.

---

## Calling both from Flutter

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;

const baseUrl = 'http://34.172.95.142:8000';

Future<Map<String, dynamic>> getScoredRoutes({
  required double originLat,
  required double originLng,
  required double destLat,
  required double destLng,
}) async {
  final response = await http.post(
    Uri.parse('$baseUrl/route'),
    headers: {'Content-Type': 'application/json'},
    body: jsonEncode({
      'origin': {'lat': originLat, 'lng': originLng},
      'destination': {'lat': destLat, 'lng': destLng},
    }),
  );
  if (response.statusCode != 200) {
    throw Exception('Route request failed: ${response.statusCode}');
  }
  return jsonDecode(response.body);
}

Future<Map<String, dynamic>> getNearbyLandmarks({
  required double lat,
  required double lng,
  double radiusKm = 10,
  String? sensoryRating,
  int limit = 10,
}) async {
  final params = {
    'lat': '$lat',
    'lng': '$lng',
    'radius_km': '$radiusKm',
    'limit': '$limit',
    if (sensoryRating != null) 'sensory_rating': sensoryRating,
  };
  final uri = Uri.parse('$baseUrl/landmarks/nearby').replace(queryParameters: params);

  final response = await http.get(uri);
  if (response.statusCode != 200) {
    throw Exception('Landmarks request failed: ${response.statusCode}');
  }
  return jsonDecode(response.body);
}
```

---
