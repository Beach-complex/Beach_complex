import { useState, useRef, useEffect, useMemo } from 'react';
import { ThumbsUp, AlertTriangle, Heart, Loader2 } from 'lucide-react';
import { Beach } from '../types/beach';
import { BottomNavigation } from './BottomNavigation';
import { MonthlyHeatmap } from './MonthlyHeatmap';
import { Calendar } from './ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './ui/dialog';
import { Toaster } from './ui/sonner';
import { fetchRecentConditions, type BeachConditionDto } from '../api/conditions';
import MapView from '@/components/MapView';
import { useUserLocation } from '../hooks/useUserLocation';

interface BeachDetailViewProps {
  beach: Beach;
  beaches: Beach[];
  onClose: () => void;
  selectedDate: Date | undefined;
  weatherTemp: string;
  reservationEventId?: string | null;
  reservationPreferredHour?: number | null;
  onDateChange?: (date: Date | undefined) => void;
  onNavigate?: (view: string) => void;
  onBeachChange?: (beach: Beach) => void;
  favoriteBeaches?: string[];
  onFavoriteToggle?: (beachId: string) => void;
  isAuthenticated?: boolean;
  onRequireAuth?: () => void;
}

const STATUS_COLORS = {
  busy: '#FF0000',
  normal: '#FFEA00',
  free: '#51FF00',
  unknown: '#64748B',
} as const;

const TELEMETRY_DEFAULT_MAX_WAVE_HEIGHT = 1.5;

const formatCoordinates = (latitude: number, longitude: number) => {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return '좌표 정보 없음';
  }
  return `${latitude.toFixed(3)}°, ${longitude.toFixed(3)}°`;
};

const formatUpdatedAt = (updatedAt: string) => {
  if (!updatedAt) return '업데이트 정보 없음';
  const parsed = new Date(updatedAt);
  if (Number.isNaN(parsed.getTime())) return '업데이트 정보 없음';
  return `업데이트: ${parsed.toLocaleString('ko-KR')}`;
};

function CloudWeatherIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 50 50" fill="none">
      <path
        d="M36.4125 20.8333H36.4583C38.9447 20.8333 41.3293 21.8211 43.0875 23.5792C44.8456 25.3374 45.8333 27.7219 45.8333 30.2083C45.8333 32.6947 44.8456 35.0793 43.0875 36.8375C41.3293 38.5956 38.9447 39.5833 36.4583 39.5833H14.5833C11.907 39.5839 9.3331 38.5542 7.39551 36.708C5.45792 34.8617 4.30529 32.3405 4.17668 29.6672C4.04807 26.994 4.95334 24.3738 6.70474 22.35C8.45614 20.3263 10.9193 19.0543 13.5833 18.7979M36.4125 20.8333C36.4417 20.4903 36.4569 20.1431 36.4583 19.7917C36.4629 16.8358 35.325 13.9925 33.2823 11.8559C31.2397 9.71934 28.4504 8.45479 25.4973 8.3265C22.5442 8.19821 19.6558 9.21611 17.4356 11.1675C15.2154 13.1189 13.8351 15.8528 13.5833 18.7979M36.4125 20.8333C36.2028 23.128 35.3061 25.3061 33.8396 27.0833M13.5833 18.7979C13.9125 18.766 14.2458 18.75 14.5833 18.75C16.9292 18.75 19.0937 19.525 20.8333 20.8333"
        stroke="#007DFC"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
    </svg>
  );
}

function makeDemoConditionsFor24h(beach: Beach): BeachConditionDto[] {
  const now = Date.now();
  const weather = ['맑음', '구름 조금', '흐림'];
  let base = 0.35 + Math.random() * 0.25;

  const arr = Array.from({ length: 24 }, (_, i) => {
    base += (Math.random() - 0.5) * 0.1;
    base = Math.max(0.1, Math.min(1.1, base));
    const t = new Date(now - (23 - i) * 3600_000).toISOString();
    return {
      observedAt: t,
      waveHeightMeters: +base.toFixed(2),
      waterTemperatureCelsius: +(18 + Math.random() * 6).toFixed(1),
      latitude: beach.latitude,
      longitude: beach.longitude,
      weatherSummary: weather[Math.floor(Math.random() * weather.length)],
    } as unknown as BeachConditionDto;
  });

  return arr;
}



type ConditionStatus = 'free' | 'normal' | 'busy';
type ExtendedConditionStatus = ConditionStatus | 'unknown';

interface HourlyTelemetryPoint {
  hour: number;
  status: ExtendedConditionStatus;
  percentage: number;
  waveHeight: number | null;
  observedAt: string | null;
}

function classifyWaveHeight(waveHeight?: number | null): ConditionStatus {
  if (waveHeight == null) return 'free';
  if (waveHeight < 0.4) return 'free';
  if (waveHeight < 1.0) return 'normal';
  return 'busy';
}

function getStatusLabel(status: ExtendedConditionStatus) {
  switch (status) {
    case 'busy':
      return '혼잡';
    case 'normal':
      return '보통';
    case 'free':
      return '여유';
    default:
      return '정보 없음';
  }
}

function formatWaterTemperature(value: number | null | undefined) {
  return value == null ? '데이터 없음' : `${value.toFixed(1)}°C`;
}

function formatWaveHeight(value: number | null | undefined) {
  return value == null ? '데이터 없음' : `${value.toFixed(1)}m`;
}

function formatObservedAt(value: string | null | undefined) {
  if (!value) return '관측 시간 정보 없음';
  const date = new Date(value);
  return date.toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatHourRange(hour: number) {
  const start = hour.toString().padStart(2, '0');
  const end = ((hour + 1) % 24).toString().padStart(2, '0');
  return `${start}:00~${end}:00`;
}

export function BeachDetailView({
  beach,
  beaches: allBeaches,
  onClose,
  selectedDate,
  weatherTemp,
  reservationEventId,
  reservationPreferredHour,
  onDateChange,
  onNavigate,
  onBeachChange,
  favoriteBeaches = [],
  onFavoriteToggle,
  isAuthenticated,
  onRequireAuth,
}: BeachDetailViewProps) {
  const [activeTab, setActiveTab] = useState('home');
  const [sheetHeight, setSheetHeight] = useState(220);
  const [isDragging, setIsDragging] = useState(false);
  const [showWeather, setShowWeather] = useState(false);
  const [datePickerOpen, setDatePickerOpen] = useState(false);
  const [localDate, setLocalDate] = useState<Date | undefined>(selectedDate);
  const startY = useRef(0);
  const startHeight = useRef(0);
  const sheetContentRef = useRef<HTMLDivElement>(null);
  const monthlyHeatmapRef = useRef<HTMLDivElement>(null);
  const [conditions, setConditions] = useState<BeachConditionDto[]>([]);
  const [isLoadingConditions, setIsLoadingConditions] = useState(false);
  const [conditionsError, setConditionsError] = useState<string | null>(null);
  const beachTelemetryId = beach.id || null;

  const { coords } = useUserLocation();

  useEffect(() => {
    let ignore = false;

    if (!beachTelemetryId) {
      setConditions([]);
      setConditionsError(null);
      setIsLoadingConditions(false);
      return;
    }

    setIsLoadingConditions(true);
    setConditionsError(null);
    setConditions([]);

    fetchRecentConditions(beachTelemetryId)
      .then((data) => {
        if (ignore) return;
        setConditions(data);
      })
      .catch((error) => {
        if (ignore) return;
        console.error('Failed to fetch beach conditions', error);
        setConditions([]);
        setConditionsError('관측 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
      })
      .finally(() => {
        if (!ignore) setIsLoadingConditions(false);
      });

    return () => {
      ignore = true;
    };
  }, [beachTelemetryId]);

  const sortedConditions = useMemo(() => {
    const source =
      conditions.length > 0
        ? conditions
        : makeDemoConditionsFor24h(beach);

    return [...source].sort(
      (a, b) => new Date(b.observedAt).getTime() - new Date(a.observedAt).getTime(),
    );
  }, [conditions, beach]);


  const latestCondition = sortedConditions[0] ?? null;

  const { hourlyData: hourlyConditions, bestHour, worstHour } = useMemo(() => {
    if (sortedConditions.length === 0) {
      return {
        hourlyData: Array.from({ length: 24 }, (_, hour) => ({
          hour,
          status: 'unknown' as ExtendedConditionStatus,
          percentage: 0,
          waveHeight: null,
          observedAt: null,
        })),
        bestHour: null,
        worstHour: null,
      };
    }

    const heights = sortedConditions
      .map((c) => c.waveHeightMeters)
      .filter((v): v is number => typeof v === 'number');

    const referenceMax =
      heights.length > 0
        ? Math.max(TELEMETRY_DEFAULT_MAX_WAVE_HEIGHT, ...heights)
        : TELEMETRY_DEFAULT_MAX_WAVE_HEIGHT;

    const latestByHour = new Map<number, BeachConditionDto>();
    sortedConditions.forEach((c) => {
      const hour = new Date(c.observedAt).getHours();
      const existing = latestByHour.get(hour);
      if (!existing || +new Date(c.observedAt) > +new Date(existing.observedAt)) {
        latestByHour.set(hour, c);
      }
    });

    const hourlySeries: HourlyTelemetryPoint[] = Array.from({ length: 24 }, (_, hour) => {
      const snapshot = latestByHour.get(hour);
      if (!snapshot) {
        return { hour, status: 'unknown', percentage: 0, waveHeight: null, observedAt: null };
      }
      const waveHeight = snapshot.waveHeightMeters ?? null;
      if (waveHeight == null) {
        return { hour, status: 'unknown', percentage: 0, waveHeight: null, observedAt: snapshot.observedAt };
      }
      const status = classifyWaveHeight(waveHeight);
      const percentage = Math.min(Math.round((waveHeight / referenceMax) * 100), 100);
      return { hour, status, percentage, waveHeight, observedAt: snapshot.observedAt };
    });

    const availableHours = hourlySeries.filter((e) => e.waveHeight != null);
    const calmest = availableHours.length
      ? availableHours.reduce((p, c) => (c.waveHeight! < p.waveHeight! ? c : p))
      : null;
    const roughest = availableHours.length
      ? availableHours.reduce((p, c) => (c.waveHeight! > p.waveHeight! ? c : p))
      : null;

    return { hourlyData: hourlySeries, bestHour: calmest, worstHour: roughest };
  }, [sortedConditions]);

  const heatmapHourlyData = useMemo(
    () =>
      hourlyConditions.map(({ hour, status, percentage }) => ({
        hour,
        status: status === 'unknown' ? 'free' : status,
        percentage,
      })),
    [hourlyConditions],
  );


  const displayStatus: ExtendedConditionStatus = beach.status;

  const statusTextColor =
    displayStatus === 'busy'
      ? '#FF0000'
      : displayStatus === 'normal'
      ? '#FFA500'
      : displayStatus === 'free'
      ? '#51FF00'
      : '#6B7280';

  const latestWaterTemperature = latestCondition?.waterTemperatureCelsius;
  const temperatureDisplay = latestCondition ? formatWaterTemperature(latestWaterTemperature) : weatherTemp;
  const waveHeightDisplay = formatWaveHeight(latestCondition?.waveHeightMeters ?? null);
  const observedAtDisplay = latestCondition ? formatObservedAt(latestCondition.observedAt) : null;
  const weatherSummary = latestCondition?.weatherSummary ?? '관측 데이터가 연결되는 중입니다.';
  const isTelemetryConfigured = Boolean(beachTelemetryId);
  const hasHourlyObservations = hourlyConditions.some((e) => e.waveHeight != null);
  const bestHourRange = bestHour ? formatHourRange(bestHour.hour) : null;
  const worstHourRange = worstHour ? formatHourRange(worstHour.hour) : null;
  const bestHourStatus = bestHour ? getStatusLabel(bestHour.status) : null;
  const worstHourStatus = worstHour ? getStatusLabel(worstHour.status) : null;
  const bestHourWave = bestHour?.waveHeight ?? null;
  const worstHourWave = worstHour?.waveHeight ?? null;
  const recommendationFallback = isTelemetryConfigured ? '관측 데이터가 부족해요' : '센서 연결 후 이용 가능해요';
  const observationLocation =
    latestCondition && latestCondition.latitude != null && latestCondition.longitude != null
      ? `${latestCondition.latitude.toFixed(3)}, ${latestCondition.longitude.toFixed(3)}`
      : null;

  const formatDate = (date: Date | undefined) => {
    if (!date) return '날짜';
    const month = date.getMonth() + 1;
    const day = date.getDate();
    return `${month}/${day}`;
  };

  const handleDragStart = (e: React.MouseEvent | React.TouchEvent) => {
    setIsDragging(true);
    const y = 'touches' in e ? e.touches[0].clientY : e.clientY;
    startY.current = y;
    startHeight.current = sheetHeight;
  };

  const handleDragMove = (e: MouseEvent | TouchEvent) => {
    if (!isDragging) return;
    const y = 'touches' in e ? e.touches[0].clientY : (e as MouseEvent).clientY;
    const diff = startY.current - y;
    const newHeight = Math.min(Math.max(startHeight.current + diff, 160), 650);
    setSheetHeight(newHeight);
  };

  const handleDragEnd = () => setIsDragging(false);

  useEffect(() => {
    if (!isDragging) return;
    window.addEventListener('mousemove', handleDragMove);
    window.addEventListener('mouseup', handleDragEnd);
    window.addEventListener('touchmove', handleDragMove);
    window.addEventListener('touchend', handleDragEnd);
    return () => {
      window.removeEventListener('mousemove', handleDragMove);
      window.removeEventListener('mouseup', handleDragEnd);
      window.removeEventListener('touchmove', handleDragMove);
      window.removeEventListener('touchend', handleDragEnd);
    };
  }, [isDragging]);

  useEffect(() => {
    const sheetContent = sheetContentRef.current;
    if (!sheetContent) return;

    const onScroll = () => {
      const monthly = monthlyHeatmapRef.current;
      if (!monthly) return;
      const scrollTop = sheetContent.scrollTop;
      const monthlyTop = monthly.offsetTop - sheetContent.offsetTop - 100;
      setActiveTab(scrollTop >= monthlyTop ? 'monthly' : 'home');
    };

    sheetContent.addEventListener('scroll', onScroll);
    return () => sheetContent.removeEventListener('scroll', onScroll);
  }, []);

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    if (tab === 'home') {
      sheetContentRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    if (tab === 'search') {
      onClose();
      return;
    }
    if (tab === 'events') {
      onNavigate?.('events');
      return;
    }
    if (tab === 'mypage') {
      onNavigate?.('mypage');
      return;
    }
  };

  return (
    <div className="fixed inset-0 bg-background z-50 max-w-[480px] mx-auto">
      <div className="relative bg-card p-3 border-b border-border z-30">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0 flex-shrink">
            <div className="shrink-0">
              <svg width="36" height="36" viewBox="0 0 40 40" fill="none">
                <circle cx="20" cy="20" r="20" fill="#007DFC" />
                <path d="M10 22C12 20 14 20 16 22C18 24 20 24 22 22C24 20 26 20 28 22C29 23 30 23 31 22" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
                <path d="M10 27C12 25 14 25 16 27C18 29 20 29 22 27C24 25 26 25 28 27C29 28 30 28 31 27" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
              </svg>
            </div>
            <div className="min-w-0">
              <h1 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[15px] leading-tight text-foreground whitespace-nowrap">비치체크</h1>
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] leading-tight text-muted-foreground truncate">{beach.name}</p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <Popover open={datePickerOpen} onOpenChange={setDatePickerOpen}>
              <PopoverTrigger asChild>
                <button className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card dark:bg-gray-800 rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border">
                  <svg width="14" height="14" viewBox="0 0 16 16" fill="none" className="shrink-0">
                    <rect x="2" y="3" width="12" height="11" rx="2" stroke="#007DFC" strokeWidth="1.5" />
                    <path d="M5 1V4M11 1V4M2 6H14" stroke="#007DFC" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                  <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-foreground whitespace-nowrap truncate">
                    {(() => {
                      const d = localDate;
                      if (!d) return '날짜';
                      const m = d.getMonth() + 1;
                      const day = d.getDate();
                      return `${m}/${day}`;
                    })()}
                  </span>
                </button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="end">
                <Calendar
                  mode="single"
                  selected={localDate}
                  onSelect={(date) => {
                    setLocalDate(date);
                    onDateChange?.(date);
                    setDatePickerOpen(false);
                  }}
                  initialFocus
                />
              </PopoverContent>
            </Popover>

            <button
              onClick={() => setShowWeather(true)}
              className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card dark:bg-gray-800 rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border"
              title="날씨 보기"
            >
              <CloudWeatherIcon />
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-foreground whitespace-nowrap">
                {temperatureDisplay}
              </span>
            </button>
          </div>
        </div>
      </div>

      <div className="absolute inset-0 top-[73px] bottom-0 overflow-hidden">
        <MapView
          beaches={allBeaches}
          selected={beach}
          onSelect={(b) => onBeachChange?.(b)}
          userCoords={coords}
        />
      </div>

      <div
        className="absolute left-0 right-0 bg-white rounded-t-[20px] shadow-[0px_-4px_16px_rgba(0,0,0,0.15)] transition-none z-20"
        style={{ bottom: 64, height: sheetHeight }}
      >
        <div
          className="flex justify-center pt-3 pb-2 cursor-grab active:cursor-grabbing bg-card dark:bg-gray-800 rounded-t-[20px]"
          onMouseDown={(e) => {
            setIsDragging(true);
            startY.current = e.clientY;
            startHeight.current = sheetHeight;
          }}
          onTouchStart={(e) => {
            setIsDragging(true);
            startY.current = e.touches[0].clientY;
            startHeight.current = sheetHeight;
          }}
        >
          <div className="w-12 h-1.5 bg-muted-foreground rounded-full" />
        </div>

        <div
          ref={sheetContentRef}
          className="px-4 pt-3 pb-4 overflow-y-auto bg-muted dark:bg-gray-900"
          style={{ height: sheetHeight - 28 }}
        >
          <div className="mb-4 bg-card dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-border">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 flex-1 min-w-0">
                <div className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: STATUS_COLORS[displayStatus] }} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <h2 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-foreground">{beach.name}</h2>
                    <button
                      onClick={() => onFavoriteToggle?.(beach.id)}
                      className="shrink-0 p-1 hover:bg-accent rounded-full transition-colors"
                      aria-label="찜하기"
                    >
                      <Heart
                        className={`w-4 h-4 transition-colors ${
                          (favoriteBeaches || []).includes(beach.id)
                            ? 'fill-purple-600 stroke-purple-600'
                            : 'fill-none stroke-gray-400 hover:stroke-purple-500'
                        }`}
                      />
                    </button>
                  </div>
                  <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[12px] text-muted-foreground">
                    {beach.code || '코드 정보 없음'} · {formatCoordinates(beach.latitude, beach.longitude)}
                  </p>
                  <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground">
                    {formatUpdatedAt(beach.updatedAt)}
                  </p>
                  {observedAtDisplay && (
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mt-1">
                      최근 예측 {observedAtDisplay} · 혼잡도 {waveHeightDisplay}
                    </p>
                  )}
                </div>
              </div>
              <div className="text-right shrink-0 ml-2">
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px]" style={{ color: statusTextColor }}>
                  {getStatusLabel(displayStatus)}
                </p>
              </div>
            </div>
          </div>

          <div className="mb-4">
            <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">실시간 관측</h3>
            <div className="bg-card dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-border">
              {isLoadingConditions ? (
                <div className="flex items-center gap-2 text-muted-foreground">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px]">관측 데이터를 불러오는 중이에요...</span>
                </div>
              ) : latestCondition ? (
                <div className="space-y-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-1">날씨 요약</p>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-foreground text-[14px]">{weatherSummary}</p>
                    </div>
                    <div className="text-right">
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-1">관측 시각</p>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-foreground">{observedAtDisplay}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-3 gap-3">
                    <div className="bg-muted/60 dark:bg-gray-900/50 rounded-lg p-3 text-center">
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-1">수온</p>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[15px] text-foreground">
                        {formatWaterTemperature(latestWaterTemperature)}
                      </p>
                    </div>
                    <div className="bg-muted/60 dark:bg-gray-900/50 rounded-lg p-3 text-center">
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-1">파고</p>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[15px] text-foreground">{waveHeightDisplay}</p>
                    </div>
                    <div className="bg-muted/60 dark:bg-gray-900/50 rounded-lg p-3 text-center">
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-1">현재 상태</p>
                      <p className="font-['Noto_SANS_KR:Bold',_sans-serif] text-[15px]" style={{ color: statusTextColor }}>
                        {getStatusLabel(displayStatus)}
                      </p>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-8 text-center text-muted-foreground">
                  <AlertTriangle className="w-6 h-6" />
                  <div>
                    <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px]">
                      {isTelemetryConfigured ? '최근 24시간의 관측 데이터가 없어요.' : '이 해수욕장은 아직 관측 데이터가 연결되지 않았어요.'}
                    </p>
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mt-1">
                      센서 연결 상태를 확인한 뒤 다시 시도해주세요.
                    </p>
                  </div>
                </div>
              )}

              {conditionsError && (
                <p className="mt-4 font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-red-500">
                  {conditionsError}
                </p>
              )}
            </div>
          </div>

          <div className="mb-4">
            <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">추천시간</h3>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-card dark:bg-gray-800 rounded-xl p-3 flex items-center gap-3 shadow-sm border border-border">
                <div className="shrink-0 w-10 h-10 bg-blue-100 dark:bg-blue-900/50 rounded-full flex items-center justify-center">
                  <ThumbsUp className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                </div>
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-0.5">방문 추천</p>
                  {bestHour ? (
                    <>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-foreground">{bestHourRange}</p>
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">
                        파고 {formatWaveHeight(bestHourWave)} · {bestHourStatus}
                      </p>
                    </>
                  ) : (
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">{recommendationFallback}</p>
                  )}
                </div>
              </div>

              <div className="bg-card dark:bg-gray-800 rounded-xl p-3 flex items-center gap-3 shadow-sm border border-border">
                <div className="shrink-0 w-10 h-10 bg-red-100 dark:bg-red-900/50 rounded-full flex items-center justify-center">
                  <AlertTriangle className="w-5 h-5 text-red-600 dark:text-red-400" />
                </div>
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-0.5">혼잡 시간대</p>
                  {worstHour ? (
                    <>
                      <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-foreground">{worstHourRange}</p>
                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">
                        파고 {formatWaveHeight(worstHourWave)} · {worstHourStatus}
                      </p>
                    </>
                  ) : (
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">{recommendationFallback}</p>
                  )}
                </div>
              </div>
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-foreground">최근 24시간 혼잡도 추이</h3>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">
                  현재 {new Date().getHours()}시
                </span>
              </div>
            </div>

            <div className="bg-card dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-border">
              {isLoadingConditions && !hasHourlyObservations ? (
                <div className="flex items-center justify-center gap-2 py-12 text-muted-foreground">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px]">그래프를 준비하는 중입니다...</span>
                </div>
              ) : hasHourlyObservations ? (
                <>
                  <div className="flex flex-wrap items-center justify-center gap-4 mb-4 pb-4 border-b border-border">
                    <div className="flex items-center gap-2">
                      <div className="w-5 h-5 rounded-md" style={{ backgroundColor: STATUS_COLORS.free }} />
                      <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">여유 (&lt;0.4m)</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="w-5 h-5 rounded-md" style={{ backgroundColor: STATUS_COLORS.normal }} />
                      <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">보통 (0.4~1.0m)</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="w-5 h-5 rounded-md" style={{ backgroundColor: STATUS_COLORS.busy }} />
                      <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">혼잡 (≥1.0m)</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="w-5 h-5 rounded-md" style={{ backgroundColor: STATUS_COLORS.unknown }} />
                      <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">데이터 없음</span>
                    </div>
                  </div>

                  <div className="relative">
                    <div className="flex items-end justify-between gap-[2px] h-[180px]">
                      {hourlyConditions.map((d) => {
                        const isCurrentHour = d.hour === new Date().getHours();
                        const barHeight = (d.percentage / 100) * 160;
                        return (
                          <div key={d.hour} className="flex flex-col items-center gap-2 flex-1 relative group">
                            <div className="absolute bottom-full mb-2 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10">
                              <div className="bg-gray-900 text-white px-3 py-2 rounded-lg shadow-lg whitespace-nowrap">
                                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px]">{d.hour}시</p>
                                {d.waveHeight != null ? (
                                  <>
                                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px]">
                                      파고 {formatWaveHeight(d.waveHeight)} · {getStatusLabel(d.status)}
                                    </p>
                                    {d.observedAt && (
                                      <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-gray-300 mt-1">
                                        관측 {formatObservedAt(d.observedAt)}
                                      </p>
                                    )}
                                  </>
                                ) : (
                                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px]">데이터 없음</p>
                                )}
                                <div className="absolute left-1/2 -translate-x-1/2 -bottom-1 w-2 h-2 bg-gray-900 rotate-45" />
                              </div>
                            </div>

                            <div
                              className="w-full rounded-t-md transition-all duration-300 hover:opacity-80 relative"
                              style={{
                                backgroundColor: STATUS_COLORS[d.status],
                                height: `${barHeight}px`,
                                minHeight: d.waveHeight != null ? '8px' : '6px',
                                boxShadow: isCurrentHour ? '0 0 0 2px #007DFC' : 'none',
                                opacity: d.waveHeight == null ? 0.3 : 1,
                              }}
                            >
                              {isCurrentHour && (
                                <div className="absolute -top-6 left-1/2 -translate-x-1/2 whitespace-nowrap">
                                  <div className="bg-blue-500 text-white px-2 py-1 rounded text-[10px] font-['Noto_Sans_KR:Bold',_sans-serif]">지금</div>
                                </div>
                              )}
                            </div>

                            <span className={`font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] ${isCurrentHour ? 'text-blue-600 dark:text-blue-400 font-bold' : 'text-muted-foreground'}`}>
                              {d.hour}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  <div className="flex items-center justify-center mt-3 pt-3 border-t border-border">
                    <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">시간대별 혼잡도 예측 (0시 ~ 23시)</span>
                  </div>
                </>
              ) : (
                <div className="flex flex-col items-center justify-center gap-3 py-10 text-center text-muted-foreground">
                  <AlertTriangle className="w-6 h-6" />
                  <div>
                    <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px]">
                      {isTelemetryConfigured ? '24시간 동안 수집된 파고 데이터가 없어요.' : '센서 연결을 기다리고 있어요.'}
                    </p>
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mt-1">실시간 데이터가 수집되면 그래프가 자동으로 업데이트돼요.</p>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div ref={monthlyHeatmapRef} className="mt-4">
            <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">월간 혼잡도</h3>
            <MonthlyHeatmap
              month={localDate ? localDate.getMonth() + 1 : new Date().getMonth() + 1}
              year={localDate ? localDate.getFullYear() : new Date().getFullYear()}
              beachName={beach.name}
              beachId={beach.id}
              eventId={reservationEventId ?? null}
              preferredHour={reservationPreferredHour ?? null}
              hourlyData={heatmapHourlyData}
              onMonthChange={(date) => {
                setLocalDate(date);
                onDateChange?.(date);
              }}
              onDateSelect={(date) => {
                const newDate = new Date(date.year, date.month - 1, date.date);
                setLocalDate(newDate);
                onDateChange?.(newDate);
              }}
              externalDate={localDate}
              isAuthenticated={isAuthenticated}
              onAuthRequired={onRequireAuth}
            />
          </div>
        </div>
      </div>

      <BottomNavigation activeTab={activeTab} onTabChange={handleTabChange} />
      <Toaster position="top-center" />

      <Dialog open={showWeather} onOpenChange={setShowWeather}>
        <DialogContent className="max-w-[340px]">
          <DialogHeader>
            <DialogTitle className="font-['Noto_Sans_KR:Bold',_sans-serif]">오늘의 날씨</DialogTitle>
          </DialogHeader>
          <div className="py-6 flex flex-col items-center gap-4">
            <div className="bg-gradient-to-br from-blue-100 to-blue-50 p-6 rounded-full">
              <CloudWeatherIcon />
            </div>
            {isLoadingConditions && !latestCondition ? (
              <div className="flex items-center gap-2 text-muted-foreground">
                <Loader2 className="w-4 h-4 animate-spin" />
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px]">관측 데이터를 불러오는 중이에요...</span>
              </div>
            ) : latestCondition ? (
              <div className="text-center space-y-4 w-full">
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-gray-600 mb-1">날씨 요약</p>
                  <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[16px] text-foreground">{weatherSummary}</p>
                  {observedAtDisplay && (
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-muted-foreground mt-1">관측 {observedAtDisplay}</p>
                  )}
                </div>

                <div className="grid grid-cols-3 gap-4 pt-2">
                  <div>
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">수온</p>
                    <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-foreground">{formatWaterTemperature(latestWaterTemperature)}</p>
                  </div>
                  <div>
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">혼잡도</p>
                    <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-foreground">{waveHeightDisplay}</p>
                  </div>
                  <div>
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">현재 상태</p>
                    <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px]" style={{ color: statusTextColor }}>{getStatusLabel(displayStatus)}</p>
                  </div>
                </div>

                {observationLocation && (
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">관측 위치 {observationLocation}</p>
                )}
              </div>
            ) : (
              <div className="text-center space-y-2 text-muted-foreground">
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px]">표시할 관측 데이터가 아직 없어요.</p>
                <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px]">센서가 데이터를 수집하면 자동으로 업데이트돼요.</p>
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
