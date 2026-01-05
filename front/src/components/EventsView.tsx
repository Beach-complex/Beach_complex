import { useState } from 'react';
import { Calendar } from 'lucide-react';
import svgPaths from "../imports/svg-aou00tt65r";
import { BottomNavigation } from './BottomNavigation';
import { EventCalendar } from './EventCalendar';
import { EventCard } from './EventCard';
import { events, type Event } from '../data/events';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from './ui/dialog';
import { Toaster } from './ui/sonner';

interface EventsViewProps {
  onNavigate: (view: string) => void;
  onReserve?: (event: Event) => void;
}

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

export function EventsView({ onNavigate, onReserve }: EventsViewProps) {
  const [activeTab, setActiveTab] = useState('events');
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(new Date());
  const [showWeather, setShowWeather] = useState(false);

  const mockWeather = {
    temp: '28°C',
    condition: '맑음',
    humidity: '65%',
    wind: '3m/s',
  };

  const formatDate = (date: Date | undefined) => {
    if (!date) return '날짜';
    const month = date.getMonth() + 1;
    const day = date.getDate();
    return `${month}/${day}`;
  };

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    if (tab === 'home') {
      onNavigate('main');
    } else if (tab === 'search') {
      onNavigate('main');
    } else if (tab === 'mypage') {
      onNavigate('mypage');
    }
  };

  return (
    <div className="relative min-h-screen bg-background max-w-[480px] mx-auto pb-20">
      {/* Header */}
      <div className="relative bg-card p-3 border-b border-border">
        <div className="flex items-center justify-between gap-2">
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
            <button 
              className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card dark:bg-gray-800 rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border"
            >
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none" className="shrink-0">
                <rect x="2" y="3" width="12" height="11" rx="2" stroke="#007DFC" strokeWidth="1.5" />
                <path d="M5 1V4M11 1V4M2 6H14" stroke="#007DFC" strokeWidth="1.5" strokeLinecap="round" />
              </svg>
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[10px] text-foreground whitespace-nowrap truncate">
                {formatDate(selectedDate)}
              </span>
            </button>
            
            <button 
              onClick={() => setShowWeather(true)}
              className="flex items-center justify-center gap-1.5 w-[85px] h-[38px] px-2 bg-card dark:bg-gray-800 rounded-lg shadow-sm hover:shadow-md transition-shadow border border-border"
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
      </div>

      {/* Calendar Section */}
      <div className="p-4">
        <EventCalendar 
          events={events}
          selectedDate={selectedDate}
          onDateSelect={setSelectedDate}
        />
      </div>

      {/* Events List */}
      <div className="px-4 pb-4">
        <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">주변 행사 / 활동 추천</h3>
        <div className="bg-card dark:bg-gray-800 rounded-xl shadow-sm divide-y divide-border border border-border overflow-hidden">
          {events.map((event) => (
            <EventCard key={event.id} event={event} onReserve={onReserve} />
          ))}
        </div>
      </div>

      {/* Bottom Navigation */}
      <BottomNavigation activeTab={activeTab} onTabChange={handleTabChange} />
      <Toaster position="top-center" />

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
    </div>
  );
}
