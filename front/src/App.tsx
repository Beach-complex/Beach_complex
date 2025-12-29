// front/src/App.tsx
import { useState, useEffect, useMemo } from 'react';
import { Search, Heart } from 'lucide-react';
import svgPaths from "./imports/svg-aou00tt65r";
import { BeachCard } from './components/BeachCard';
// import { HashtagChip } from './components/HashtagChip'; // ⬅️ 사용 안 함
import { BeachDetailView } from './components/BeachDetailView';
import { EventsView } from './components/EventsView';
import { MyPageView } from './components/MyPageView';
import { DeveloperModeView } from './components/DeveloperModeView';
import { BottomNavigation } from './components/BottomNavigation';
import { fetchBeaches } from './data/beaches';
import { Beach } from './types/beach';
import { Calendar } from './components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from './components/ui/popover';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './components/ui/dialog';
// import { computeTrendingScore } from './constants/trending'; // ⬅️ 큐레이션으로 대체하여 사용 안 함
import HashtagBar, { FilterKey } from './components/HashtagBar';
import { useUserLocation } from './hooks/useUserLocation';
import { favoritesApi } from './api/favorites';

/** =======================
 *  큐레이션 상수 (요청 사양)
 *  ======================= */
// #요즘뜨는해수욕장 → 광안리, 송도, 다대포 (이 순서 유지)
const TRENDING_ORDER = ['GWANGALLI', 'SONGDO'] as const;
const TRENDING_SET = new Set(TRENDING_ORDER);

// #가장많이가는곳 → 해운대, 광안리 (이 순서 유지)
const POPULAR_ORDER = ['HAEUNDAE', 'GWANGALLI'] as const;
const POPULAR_SET = new Set(POPULAR_ORDER);

// #축제하는곳 (필요 시 코드 추가)
const FESTIVAL_SET = new Set<string>(['HAEUNDAE']);

function WaveLogo() {
  return (
    <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
      <circle cx="20" cy="20" r="20" fill="#007DFC" />
      <path
        d="M10 22C12 20 14 20 16 22C18 24 20 24 22 22C24 20 26 20 28 22C29 23 30 23 31 22"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M10 27C12 25 14 25 16 27C18 29 20 29 22 27C24 25 26 25 28 27C29 28 30 28 31 27"
        stroke="white"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

function CloudWeatherIcon() {
  return (
    <svg width="32" height="32" viewBox="0 0 50 50" fill="none">
      <path
        d={svgPaths.p2a8354c0}
        stroke="#007DFC"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
    </svg>
  );
}

export default function App() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(new Date());
  const [showWeather, setShowWeather] = useState(false);
  const [datePickerOpen, setDatePickerOpen] = useState(false);
  const [beaches, setBeaches] = useState<Beach[]>([]);
  const [isLoadingBeaches, setIsLoadingBeaches] = useState(true);
  const [beachError, setBeachError] = useState<string | null>(null);
  const [selectedBeach, setSelectedBeach] = useState<Beach | null>(null);
  const [lastSelectedBeach, setLastSelectedBeach] = useState<Beach | null>(null);
  const [currentView, setCurrentView] = useState<'main' | 'events' | 'mypage' | 'developer'>('main');
  const [activeTab, setActiveTab] = useState('search'); // Start with search tab active
  const [favoriteBeaches, setFavoriteBeaches] = useState<string[]>([]);
  const [showFavoritesOnly, setShowFavoritesOnly] = useState(false);

  // ✅ 새 해시태그 상태
  const [filter, setFilter] = useState<FilterKey>(null);

  const handleSearchSubmit = () => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return;
    const match = beaches.find(
      (b) => b.name.toLowerCase().includes(q) || b.code.toLowerCase().includes(q)
    );
    if (match) {
      setSelectedBeach(match);
      setLastSelectedBeach(match);
      setActiveTab('home'); // 상세 탭으로 전환 → 지도도 그 위치로 이동
    }
  };

  // ✅ 사용자 위치 가져오기
  const { coords, perm, error: locationError } = useUserLocation();

  // ✅ 서버에서 찜 목록 로드 (로그인한 경우)
  // Note: 로그인 여부 체크는 accessToken 존재로 판단
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      // 비로그인 상태: localStorage에서 찜 목록 로드
      const savedFavorites = localStorage.getItem('beachcheck_favorites');
      if (savedFavorites) {
        try {
          const parsed = JSON.parse(savedFavorites);
          if (Array.isArray(parsed)) {
            setFavoriteBeaches(parsed.map((id: unknown) => String(id)));
          }
        } catch (error) {
          console.warn('Failed to parse stored favorites', error);
        }
      }
      return;
    }

    // 로그인 상태: 서버에서 찜 목록 로드
    favoritesApi.getMyFavorites()
      .then((favorites) => {
        const favoriteIds = favorites.map(beach => beach.id);
        setFavoriteBeaches(favoriteIds);
      })
      .catch((error) => {
        console.error('Failed to load favorites from server:', error);
        // 실패 시 localStorage 폴백
        const savedFavorites = localStorage.getItem('beachcheck_favorites');
        if (savedFavorites) {
          try {
            const parsed = JSON.parse(savedFavorites);
            if (Array.isArray(parsed)) {
              setFavoriteBeaches(parsed.map((id: unknown) => String(id)));
            }
          } catch (error) {
            console.warn('Failed to parse stored favorites', error);
          }
        }
      });
  }, []);

  // ✅ 비로그인 사용자만 localStorage에 저장
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      localStorage.setItem('beachcheck_favorites', JSON.stringify(favoriteBeaches));
    }
  }, [favoriteBeaches]);

  // ✅ 위치 기반 검색
  useEffect(() => {
    // 위치 정보가 없으면 대기
    if (!coords) {
      return;
    }

    const controller = new AbortController();
    setIsLoadingBeaches(true);
    setBeachError(null);

    // 위치 기반 검색 API 호출 (반경 50km 고정)
    const params = new URLSearchParams({
      lat: coords.lat.toString(),
      lon: coords.lng.toString(),
      radiusKm: '50'
    });

    fetch(`/api/beaches?${params}`, { signal: controller.signal })
      .then((res) => {
        if (!res.ok) {
          throw new Error(`API Error: ${res.status}`);
        }
        return res.json();
      })
      .then((data: Beach[]) => {
        setBeaches(data);
        const serverFavIds = data.filter(b => b.isFavorite).map(b => b.id);
        setFavoriteBeaches(prev => Array.from(new Set([...prev, ...serverFavIds])));
        if (data.length > 0) {
          setLastSelectedBeach((previous) => previous ?? data[0] ?? null);
        }
        console.log(`✅ ${data.length}개 해수욕장 발견 (반경 50km)`);
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }

        if (error && typeof error === 'object' && 'name' in error && (error as { name: string }).name === 'AbortError') {
          return;
        }

        const message = error instanceof Error ? error.message : '해수욕장 정보를 불러오지 못했습니다.';
        setBeachError(message);
      })
      .finally(() => {
        setIsLoadingBeaches(false);
      });

    return () => controller.abort();
  }, [coords]);

  // Load and apply theme on mount
  useEffect(() => {
    const applyTheme = () => {
      if (typeof window !== 'undefined') {
        const storedTheme = localStorage.getItem('beachcheck_theme') || 'light';
        const root = document.documentElement;
        const body = document.body;

        if (storedTheme === 'dark') {
          root.classList.add('dark');
          body.classList.add('dark');
        } else if (storedTheme === 'light') {
          root.classList.remove('dark');
          body.classList.remove('dark');
        } else {
          // Developer mode - same as system mode
          const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
          if (prefersDark) {
            root.classList.add('dark');
            body.classList.add('dark');
          } else {
            root.classList.remove('dark');
            body.classList.remove('dark');
          }
        }
      }
    };

    applyTheme();

    // Listen for storage changes (when theme is changed in MyPageView)
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'beachcheck_theme') {
        applyTheme();
      }
    };

    window.addEventListener('storage', handleStorageChange);

    // Custom event for same-page theme changes
    const handleThemeChange = () => {
      applyTheme();
    };

    window.addEventListener('themechange', handleThemeChange);

    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('themechange', handleThemeChange);
    };
  }, []);

  /** ===========================
   *  검색 + 찜 + 해시태그 큐레이션
   *  =========================== */
  const filteredBeaches = useMemo(() => {
    let arr = beaches;

    // 1) 찜 필터
    if (showFavoritesOnly) {
      arr = arr.filter(b => favoriteBeaches.includes(b.id));
    }

    // 2) 검색 필터
    const q = searchQuery.trim().toLowerCase();
    if (q) {
      arr = arr.filter(b =>
        b.name.toLowerCase().includes(q) ||
        b.code.toLowerCase().includes(q)
      );
    }

    // 3) 해시태그 동작 (status와 무관하게, 지정 큐레이션만 노출)
    if (filter === 'trending') {
      // #요즘뜨는해수욕장: 광안리 → 송도 → 다대포 (순서 고정)
      arr = arr
        .filter(b => TRENDING_SET.has(b.code))
        .sort(
          (a, b) =>
            TRENDING_ORDER.indexOf(a.code as any) -
            TRENDING_ORDER.indexOf(b.code as any)
        );
    } else if (filter === 'popular') {
      // #가장많이가는곳: 해운대 → 광안리 (순서 고정)
      arr = arr
        .filter(b => POPULAR_SET.has(b.code))
        .sort(
          (a, b) =>
            POPULAR_ORDER.indexOf(a.code as any) -
            POPULAR_ORDER.indexOf(b.code as any)
        );
    } else if (filter === 'festival') {
      // #축제하는곳: 세트에 포함된 코드만
      arr = arr.filter(b => FESTIVAL_SET.has(b.code));
    }

    return arr;
  }, [beaches, favoriteBeaches, showFavoritesOnly, searchQuery, filter]);

  const toggleFavorite = async (beachId: string, e: React.MouseEvent) => {
    e.stopPropagation();

    const token = localStorage.getItem('accessToken');

    if (!token) {
      // 비로그인 사용자: localStorage 기반 찜 관리
      setFavoriteBeaches(prev => {
        if (prev.includes(beachId)) {
          return prev.filter(id => id !== beachId);
        } else {
          return [...prev, beachId];
        }
      });
      return;
    }

    // 로그인 사용자: 서버 API 호출
    try {
      const result = await favoritesApi.toggleFavorite(beachId);

      // 찜 상태 업데이트
      setFavoriteBeaches(prev => {
        if (result.isFavorite) {
          return [...prev, beachId];
        } else {
          return prev.filter(id => id !== beachId);
        }
      });

      // beaches 배열의 isFavorite도 업데이트
      setBeaches(prev => prev.map(beach =>
        beach.id === beachId ? { ...beach, isFavorite: result.isFavorite } : beach
      ));
    } catch (error) {
      console.error('Failed to toggle favorite:', error);
      // 에러 발생 시 사용자에게 알림 (선택사항)
      alert('찜 상태 변경에 실패했습니다. 다시 시도해주세요.');
    }
  };

  const formatDate = (date: Date | undefined) => {
    if (!date) return '날짜';
    const month = date.getMonth() + 1;
    const day = date.getDate();
    return `${month}/${day}`;
  };

  const mockWeather = {
    temp: '28°C',
    condition: '맑음',
    humidity: '65%',
    wind: '3m/s',
  };

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    if (tab === 'home') {
      // Go to BeachDetailView (시작화면2) with last selected beach
      const beachToSelect = lastSelectedBeach || beaches[0] || null;
      if (beachToSelect) {
        setSelectedBeach(beachToSelect);
        setLastSelectedBeach(beachToSelect);
      }
      setCurrentView('main');
    } else if (tab === 'search') {
      // Go to main search screen (시작화면1)
      setCurrentView('main');
      setSelectedBeach(null);
    } else if (tab === 'events') {
      setCurrentView('events');
      setSelectedBeach(null);
    } else if (tab === 'mypage') {
      setCurrentView('mypage');
      setSelectedBeach(null);
    }
  };

  // Show events view
  if (currentView === 'events') {
    return (
      <EventsView
        onNavigate={(view) => {
          if (view === 'main') {
            setCurrentView('main');
            setSelectedBeach(null);
            setActiveTab('search');
          } else {
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer');
            setSelectedBeach(null);
          }
        }}
      />
    );
  }

  // Show my page view
  if (currentView === 'mypage') {
    return (
      <MyPageView
        onNavigate={(view) => {
          if (view === 'main') {
            setCurrentView('main');
            setSelectedBeach(null);
            setActiveTab('search');
          } else if (view === 'developer') {
            setCurrentView('developer');
            setSelectedBeach(null);
          } else {
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer');
            setSelectedBeach(null);
          }
        }}
      />
    );
  }

  // Show developer mode view
  if (currentView === 'developer') {
    return (
      <DeveloperModeView
        onNavigate={(view) => {
          if (view === 'main') {
            setCurrentView('main');
            setSelectedBeach(null);
            setActiveTab('search');
          } else {
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer');
            setSelectedBeach(null);
          }
        }}
      />
    );
  }

  // Show beach detail view when a beach is selected
  if (selectedBeach) {
    return (
      <BeachDetailView
        beach={selectedBeach}
        beaches={beaches}
        onClose={() => {
          setSelectedBeach(null);
          setActiveTab('search'); // Set active tab to search when closing detail view
        }}
        selectedDate={selectedDate}
        weatherTemp={mockWeather.temp}
        onDateChange={setSelectedDate}
        onNavigate={(view) => {
          if (view === 'events') {
            setCurrentView('events');
            setSelectedBeach(null);
            setActiveTab('events');
          } else if (view === 'mypage') {
            setCurrentView('mypage');
            setSelectedBeach(null);
            setActiveTab('mypage');
          }
        }}
        onBeachChange={(newBeach) => {
          setSelectedBeach(newBeach);
          setLastSelectedBeach(newBeach);
        }}
        favoriteBeaches={favoriteBeaches}
        onFavoriteToggle={async (beachId) => {
          const token = localStorage.getItem('accessToken');

          if (!token) {
            // 비로그인 사용자: localStorage 기반
            setFavoriteBeaches(prev => {
              if (prev.includes(beachId)) {
                return prev.filter(id => id !== beachId);
              } else {
                return [...prev, beachId];
              }
            });
            return;
          }

          // 로그인 사용자: 서버 API 호출
          try {
            const result = await favoritesApi.toggleFavorite(beachId);

            setFavoriteBeaches(prev => {
              if (result.isFavorite) {
                return [...prev, beachId];
              } else {
                return prev.filter(id => id !== beachId);
              }
            });

            setBeaches(prev => prev.map(beach =>
              beach.id === beachId ? { ...beach, isFavorite: result.isFavorite } : beach
            ));
          } catch (error) {
            console.error('Failed to toggle favorite:', error);
            alert('찜 상태 변경에 실패했습니다. 다시 시도해주세요.');
          }
        }}
      />
    );
  }

  // ✅ 위치 권한 상태 처리
  if (perm === 'denied' && locationError) {
    return (
      <div className="relative min-h-screen bg-background max-w-[480px] mx-auto flex items-center justify-center p-8">
        <div className="text-center space-y-4">
          <div className="text-6xl">📍</div>
          <h2 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-lg">
            위치 권한이 필요합니다
          </h2>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-sm text-muted-foreground">
            내 주변 해수욕장을 찾기 위해<br />
            브라우저 설정에서 위치 권한을 허용해주세요.
          </p>
          <div className="text-xs text-muted-foreground bg-muted p-3 rounded-lg">
            현재 부산시청 기준으로 검색 중입니다
          </div>
        </div>
      </div>
    );
  }

  // ✅ 위치 정보 로딩 중
  if (!coords) {
    return (
      <div className="relative min-h-screen bg-background max-w-[480px] mx-auto flex items-center justify-center">
        <div className="text-center space-y-3">
          <div className="animate-pulse text-4xl">📍</div>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-muted-foreground">
            위치 정보를 가져오는 중입니다...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="relative min-h-screen bg-background max-w-[480px] mx-auto pb-20">
      {/* Header */}
      <div className="relative bg-gradient-to-b from-[#E8F4FF] to-[#F5F5F5] dark:from-gray-900 dark:to-gray-800 p-3 pb-5">
        {/* Logo and Date/Weather */}
        <div className="flex items-center justify-between gap-2 mb-5">
          <div className="flex items-center gap-2 min-w-0 flex-shrink">
            <div className="shrink-0">
              <svg width="36" height="36" viewBox="0 0 40 40" fill="none">
                <circle cx="20" cy="20" r="20" fill="#007DFC" />
                <path
                  d="M10 22C12 20 14 20 16 22C18 24 20 24 22 22C24 20 26 20 28 22C29 23 30 23 31 22"
                  stroke="white"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
                <path
                  d="M10 27C12 25 14 25 16 27C18 29 20 29 22 27C24 25 26 25 28 27C29 28 30 28 31 27"
                  stroke="white"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
              </svg>
            </div>
            <div className="min-w-0">
              <h1 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[15px] leading-tight text-foreground whitespace-nowrap">비치체크</h1>
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] leading-tight text-muted-foreground whitespace-nowrap">
                부산 해수욕장 혼잡도
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0">
            <Popover open={datePickerOpen} onOpenChange={setDatePickerOpen}>
              <PopoverTrigger asChild>
                <button className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border">
                  <svg width="14" height="14" viewBox="0 0 16 16" fill="none" className="shrink-0">
                    <rect x="2" y="3" width="12" height="11" rx="2" stroke="#007DFC" strokeWidth="1.5" />
                    <path d="M5 1V4M11 1V4M2 6H14" stroke="#007DFC" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                  <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-foreground whitespace-nowrap truncate">
                    {formatDate(selectedDate)}
                  </span>
                </button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="end">
                <Calendar
                  mode="single"
                  selected={selectedDate}
                  onSelect={(date) => {
                    setSelectedDate(date);
                    setDatePickerOpen(false);
                  }}
                  initialFocus
                />
              </PopoverContent>
            </Popover>

            <button
              onClick={() => setShowWeather(true)}
              className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border"
              title="날씨 보기"
            >
              <svg width="20" height="20" viewBox="0 0 50 50" fill="none" className="shrink-0">
                <path
                  d={svgPaths.p2a8354c0}
                  stroke="#007DFC"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2"
                />
              </svg>
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-foreground whitespace-nowrap">
                {mockWeather.temp}
              </span>
            </button>
          </div>
        </div>

        {/* Search Bar */}
        <div className="relative bg-card rounded-[10px] border-2 border-[#007dfc] p-3 flex items-center justify-between shadow-sm">
          <input
            type="text"
            placeholder="해수욕장 이름을 검색하세요"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSearchSubmit(); }}
            className="flex-1 outline-none bg-transparent font-['Noto_Sans_KR:Regular',_sans-serif] text-[13px] text-foreground placeholder:text-muted-foreground"
          />
          <button
            type="button"
            onClick={handleSearchSubmit}
            aria-label="검색"
            className="shrink-0"
          >
            <Search className="w-[18px] h-[18px] text-[#007DFC]" />
          </button>
        </div>

        {/* Hashtags */}
        <div className="flex gap-3 mt-4 overflow-x-auto pb-1 scrollbar-hide">
          {/* Favorite Filter Button */}
          <button
            onClick={() => {
              setShowFavoritesOnly(!showFavoritesOnly);
              if (!showFavoritesOnly) {
                setFilter(null); // 찜 보기 켤 때 다른 태그 해제
              }
            }}
            className={`shrink-0 flex items-center justify-center w-[36px] h-[36px] rounded-full transition-all border-2 ${
              showFavoritesOnly
                ? 'bg-purple-600 border-purple-600'
                : 'bg-card border-border hover:border-purple-300'
            }`}
            aria-label="찜한 해수욕장"
          >
            <Heart
              className={`w-4 h-4 ${
                showFavoritesOnly
                  ? 'fill-white stroke-white'
                  : 'fill-purple-600 stroke-purple-600'
              }`}
            />
          </button>

          {/* 새 해시태그 바 */}
          <HashtagBar value={filter} onChange={setFilter} />
        </div>
      </div>

      {/* Beach List */}
      {isLoadingBeaches && (
        <div className="p-8 text-center">
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-muted-foreground">
            해수욕장 정보를 불러오는 중입니다...
          </p>
        </div>
      )}

      {!isLoadingBeaches && beachError && (
        <div className="p-4 mx-4 my-4 text-center bg-red-100 text-red-600 rounded-lg border border-red-200">
          <p className="font-['Noto_SANS_KR:Regular',_sans-serif] text-[13px]">
            {beachError}
          </p>
        </div>
      )}

      <div className="divide-y divide-border">
        {filteredBeaches.map((beach) => (
          <BeachCard
            key={beach.id}
            beach={beach}
            userCoords={coords}
            isFavorite={favoriteBeaches.includes(beach.id)}
            onFavoriteToggle={(e) => toggleFavorite(beach.id, e)}
            onClick={() => {
              setSelectedBeach(beach);
              setLastSelectedBeach(beach);
              setActiveTab('home'); // Set active tab to home when selecting a beach
            }}
          />
        ))}
      </div>

      {!isLoadingBeaches && !beachError && filteredBeaches.length === 0 && (
        <div className="p-8 text-center">
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-muted-foreground">
            검색 결과가 없습니다.
          </p>
        </div>
      )}

      {/* Weather Dialog */}
      <Dialog open={showWeather} onOpenChange={setShowWeather}>
        <DialogContent className="max-w-[340px]">
          <DialogHeader>
            <DialogTitle className="font-['Noto_Sans_KR:Bold',_sans-serif]">
              오늘의 날씨
            </DialogTitle>
          </DialogHeader>
          <div className="py-6 flex flex-col items-center gap-4">
            <div className="bg-gradient-to-br from-blue-100 to-blue-50 p-6 rounded-full">
              <CloudWeatherIcon />
            </div>
            <div className="text-center space-y-3">
              <div>
                <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-gray-600 mb-1">
                  날씨
                </p>
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif]">
                  {mockWeather.condition}
                </p>
              </div>
              <div className="grid grid-cols-3 gap-4 pt-2">
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">
                    기온
                  </p>
                  <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px]">
                    {mockWeather.temp}
                  </p>
                </div>
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">
                    습도
                  </p>
                  <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px]">
                    {mockWeather.humidity}
                  </p>
                </div>
                <div>
                  <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-gray-600 mb-1">
                    풍속
                  </p>
                  <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px]">
                    {mockWeather.wind}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Bottom Navigation */}
      <BottomNavigation activeTab={activeTab} onTabChange={handleTabChange} />
    </div>
  );
}
