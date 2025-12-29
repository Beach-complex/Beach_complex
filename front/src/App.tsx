import { useState, useEffect, useMemo } from 'react';
import { Search, Heart } from 'lucide-react';
import svgPaths from "./imports/svg-aou00tt65r";
import { BeachCard } from './components/BeachCard';
import { BeachDetailView } from './components/BeachDetailView';
import { EventsView } from './components/EventsView';
import { MyPageView } from './components/MyPageView';
import { DeveloperModeView } from './components/DeveloperModeView';
import { AuthView } from './components/AuthView';
import { BottomNavigation } from './components/BottomNavigation';
import { fetchBeaches } from './data/beaches';
import { Beach } from './types/beach';
import { Calendar } from './components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from './components/ui/popover';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './components/ui/dialog';
import HashtagBar, { FilterKey } from './components/HashtagBar';
import { useUserLocation } from './hooks/useUserLocation';
import { clearAuth, loadAuth, type StoredAuth } from './utils/auth';

const TRENDING_ORDER = ['GWANGALLI', 'SONGDO'] as const;
const TRENDING_SET = new Set(TRENDING_ORDER);

const POPULAR_ORDER = ['HAEUNDAE', 'GWANGALLI'] as const;
const POPULAR_SET = new Set(POPULAR_ORDER);

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
  const [currentView, setCurrentView] = useState<'main' | 'events' | 'mypage' | 'developer' | 'auth'>('main');
  const [activeTab, setActiveTab] = useState('search');
  const [favoriteBeaches, setFavoriteBeaches] = useState<string[]>([]);
  const [showFavoritesOnly, setShowFavoritesOnly] = useState(false);
  const [authState, setAuthState] = useState<StoredAuth | null>(() => loadAuth());
  const [authEntryMode, setAuthEntryMode] = useState<'login' | 'signup'>('login');
  const [authNotice, setAuthNotice] = useState<string | null>(null);

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
      setActiveTab('home');
    }
  };

  const handleAuthRequest = (mode: 'login' | 'signup', notice?: string) => {
    setAuthEntryMode(mode);
    setAuthNotice(notice ?? null);
    setCurrentView('auth');
    setSelectedBeach(null);
    setActiveTab('mypage');
  };

  const handleAuthSuccess = (storedAuth: StoredAuth) => {
    setAuthState(storedAuth);
    setAuthNotice(null);
    setCurrentView('mypage');
    setSelectedBeach(null);
    setActiveTab('mypage');
  };

  const handleSignOut = () => {
    clearAuth();
    setAuthState(null);
    setShowFavoritesOnly(false);
  };

  const requireAuth = (notice: string) => {
    if (authState) {
      return true;
    }
    handleAuthRequest('login', notice);
    return false;
  };

  const isAuthenticated = Boolean(authState);

  const { coords, perm, error: locationError } = useUserLocation();

  useEffect(() => {
    if (!isAuthenticated) {
      setFavoriteBeaches([]);
      return;
    }

    if (typeof window === 'undefined') {
      return;
    }

    const savedFavorites = localStorage.getItem('beachcheck_favorites');
    if (!savedFavorites) {
      return;
    }

    try {
      const parsed = JSON.parse(savedFavorites);
      if (Array.isArray(parsed)) {
        setFavoriteBeaches(parsed.map((id: unknown) => String(id)));
      }
    } catch (error) {
      console.warn('Failed to parse stored favorites', error);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }
    if (typeof window === 'undefined') {
      return;
    }
    localStorage.setItem('beachcheck_favorites', JSON.stringify(favoriteBeaches));
  }, [favoriteBeaches, isAuthenticated]);

  useEffect(() => {
    if (!coords) {
      return;
    }

    const controller = new AbortController();
    setIsLoadingBeaches(true);
    setBeachError(null);

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
        if (isAuthenticated) {
          const serverFavIds = data.filter(b => b.isFavorite).map(b => b.id);
          setFavoriteBeaches(prev => Array.from(new Set([...prev, ...serverFavIds])));
        }
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
  }, [coords, isAuthenticated]);

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

    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'beachcheck_theme') {
        applyTheme();
      }
    };

    window.addEventListener('storage', handleStorageChange);

    const handleThemeChange = () => {
      applyTheme();
    };

    window.addEventListener('themechange', handleThemeChange);

    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener('themechange', handleThemeChange);
    };
  }, []);

    const filteredBeaches = useMemo(() => {
    let arr = beaches;

    if (showFavoritesOnly) {
      arr = arr.filter(b => favoriteBeaches.includes(b.id));
    }

    const q = searchQuery.trim().toLowerCase();
    if (q) {
      arr = arr.filter(b =>
        b.name.toLowerCase().includes(q) ||
        b.code.toLowerCase().includes(q)
      );
    }

    if (filter === 'trending') {
      arr = arr
        .filter(b => TRENDING_SET.has(b.code))
        .sort(
          (a, b) =>
            TRENDING_ORDER.indexOf(a.code as any) -
            TRENDING_ORDER.indexOf(b.code as any)
        );
    } else if (filter === 'popular') {
      arr = arr
        .filter(b => POPULAR_SET.has(b.code))
        .sort(
          (a, b) =>
            POPULAR_ORDER.indexOf(a.code as any) -
            POPULAR_ORDER.indexOf(b.code as any)
        );
    } else if (filter === 'festival') {
      arr = arr.filter(b => FESTIVAL_SET.has(b.code));
    }

    return arr;
  }, [beaches, favoriteBeaches, showFavoritesOnly, searchQuery, filter]);

  const toggleFavoriteById = (beachId: string) => {
    if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
      return;
    }
    setFavoriteBeaches(prev => {
      if (prev.includes(beachId)) {
        return prev.filter(id => id !== beachId);
      } else {
        return [...prev, beachId];
      }
    });
  };

  const toggleFavorite = (beachId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    toggleFavoriteById(beachId);
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
      const beachToSelect = lastSelectedBeach || beaches[0] || null;
      if (beachToSelect) {
        setSelectedBeach(beachToSelect);
        setLastSelectedBeach(beachToSelect);
      }
      setCurrentView('main');
    } else if (tab === 'search') {
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

  if (currentView === 'events') {
    return (
      <EventsView
        onNavigate={(view) => {
          if (view === 'main') {
            setCurrentView('main');
            setSelectedBeach(null);
            setActiveTab('search');
          } else {
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer' | 'auth');
            setSelectedBeach(null);
          }
        }}
      />
    );
  }

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
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer' | 'auth');
            setSelectedBeach(null);
          }
        }}
        authUser={authState?.user ?? null}
        onRequestAuth={handleAuthRequest}
        onSignOut={handleSignOut}
      />
    );
  }

  if (currentView === 'developer') {
    return (
      <DeveloperModeView
        onNavigate={(view) => {
          if (view === 'main') {
            setCurrentView('main');
            setSelectedBeach(null);
            setActiveTab('search');
          } else {
            setCurrentView(view as 'main' | 'events' | 'mypage' | 'developer' | 'auth');
            setSelectedBeach(null);
          }
        }}
      />
    );
  }

  if (currentView === 'auth') {
    return (
      <AuthView
        initialMode={authEntryMode}
        notice={authNotice}
        onClose={() => {
          setCurrentView('mypage');
          setSelectedBeach(null);
          setActiveTab('mypage');
          setAuthNotice(null);
        }}
        onAuthSuccess={handleAuthSuccess}
      />
    );
  }

  if (selectedBeach) {
    return (
      <BeachDetailView
        beach={selectedBeach}
        beaches={beaches}
        onClose={() => {
          setSelectedBeach(null);
          setActiveTab('search');
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
        onFavoriteToggle={toggleFavoriteById}
        isAuthenticated={isAuthenticated}
        onRequireAuth={() => handleAuthRequest('login', '캘린더에 추가하려면 로그인하세요.')}
      />
    );
  }

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
      <div className="relative bg-gradient-to-b from-[#E8F4FF] to-[#F5F5F5] dark:from-gray-900 dark:to-gray-800 p-3 pb-5">
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

        <div className="flex gap-3 mt-4 overflow-x-auto pb-1 scrollbar-hide">
          <button
            onClick={() => {
              if (!requireAuth('찜 기능을 사용하려면 로그인하세요.')) {
                return;
              }
              setShowFavoritesOnly(!showFavoritesOnly);
              if (!showFavoritesOnly) {
                setFilter(null);
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

          <HashtagBar value={filter} onChange={setFilter} />
        </div>
      </div>

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
              setActiveTab('home');
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

      <BottomNavigation activeTab={activeTab} onTabChange={handleTabChange} />
    </div>
  );
}
