import { MapContainer, TileLayer, CircleMarker, Marker, Popup, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import type { Beach } from '@/types/beach';
import L from 'leaflet';

// Vite 번들에서 기본 아이콘 경로 깨짐 방지
// @ts-ignore
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: new URL('leaflet/dist/images/marker-icon-2x.png', import.meta.url).toString(),
  iconUrl:       new URL('leaflet/dist/images/marker-icon.png', import.meta.url).toString(),
  shadowUrl:     new URL('leaflet/dist/images/marker-shadow.png', import.meta.url).toString(),
});

// ✅ 사용자 위치 마커 아이콘 (파란색)
const userLocationIcon = new L.Icon({
  iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

const statusColor = (s: Beach['status']) =>
  s === 'busy' ? '#ef4444' : s === 'normal' ? '#f59e0b' : s === 'free' ? '#22c55e' : '#64748b';

function FlyTo({ center }: { center: [number, number] }) {
  const map = useMap();
  map.flyTo(center, 13, { duration: 0.4 });
  return null;
}

type Props = {
  beaches: Beach[];
  selected?: Beach | null;
  onSelect?: (b: Beach) => void;
  userCoords?: { lat: number; lng: number } | null; // ✅ 사용자 위치 추가
};

export default function MapView({ beaches, selected, onSelect, userCoords }: Props) {
  const center: [number, number] =
    selected
      ? [selected.latitude, selected.longitude]
      : beaches.length
      ? [beaches[0].latitude, beaches[0].longitude]
      : [35.1796, 129.0756]; // 부산 시청 근처 fallback

  return (
    <MapContainer
      center={center}
      zoom={12}
      style={{ width: '100%', height: 360, borderRadius: 12, overflow: 'hidden' }}
      scrollWheelZoom
    >
      <TileLayer
        attribution="&copy; OpenStreetMap contributors"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      {selected && <FlyTo center={[selected.latitude, selected.longitude]} />}

      {/* ✅ 사용자 위치 마커 */}
      {userCoords && (
        <Marker
          position={[userCoords.lat, userCoords.lng]}
          icon={userLocationIcon}
        >
          <Popup>
            <div style={{ fontWeight: 600 }}>내 위치</div>
            <div style={{ fontSize: 12, color: '#64748b' }}>
              {userCoords.lat.toFixed(4)}, {userCoords.lng.toFixed(4)}
            </div>
          </Popup>
        </Marker>
      )}

      {/* 해수욕장 마커들 */}
      {beaches.map((b) => (
        <CircleMarker
          key={b.id}
          center={[b.latitude, b.longitude]}
          radius={12}
          pathOptions={{ color: statusColor(b.status), fillColor: statusColor(b.status), fillOpacity: 0.85 }}
          eventHandlers={{ click() { onSelect?.(b); } }}
        >
          <Popup>
            <div style={{ fontWeight: 600 }}>{b.name}</div>
            <div style={{ fontSize: 12, color: '#64748b' }}>
              {b.code} · {b.latitude.toFixed(3)}, {b.longitude.toFixed(3)}
            </div>
          </Popup>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}
