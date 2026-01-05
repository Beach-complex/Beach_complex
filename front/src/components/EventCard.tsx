import { Event } from '../data/events';

interface EventCardProps {
  event: Event;
  onReserve?: (event: Event) => void;
}

export function EventCard({ event, onReserve }: EventCardProps) {
  const statusColors = {
    busy: '#FF0000',
    normal: '#FFEA00',
    free: '#51FF00',
  };

  const statusLabels = {
    busy: '혼잡',
    normal: '보통',
    free: '여유',
  };

  return (
    <div className="flex gap-3 p-4 bg-card dark:bg-gray-800 hover:bg-accent transition-colors cursor-pointer">
      {/* Event Image */}
      <div className="shrink-0 w-[97px] h-[96px] bg-muted dark:bg-gray-700 rounded-lg overflow-hidden">
        {event.imageUrl ? (
          <img 
            src={event.imageUrl} 
            alt={event.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-blue-100 to-blue-200 dark:from-blue-900/50 dark:to-blue-800/50">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#007DFC" strokeWidth="2" className="dark:stroke-blue-400">
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
              <line x1="16" y1="2" x2="16" y2="6"/>
              <line x1="8" y1="2" x2="8" y2="6"/>
              <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
          </div>
        )}
      </div>

      {/* Event Info */}
      <div className="flex-1 flex flex-col justify-center">
        <div className="mb-2">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] mb-1 text-foreground">
            {event.title}
          </h3>
          <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-muted-foreground">
            {event.distance} · {event.schedule}
          </p>
        </div>
        
        <div className="flex items-center gap-2">
          <div 
            className="w-2 h-2 rounded-full shrink-0"
            style={{ backgroundColor: statusColors[event.congestionStatus] }}
          />
          <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[13px] text-foreground">
            {event.recommendedTime}
          </p>
        </div>
        <div className="mt-3 flex items-center justify-end">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onReserve?.(event);
            }}
            className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-[11px] font-['Noto_Sans_KR:Bold',_sans-serif] text-blue-700 transition-colors hover:border-blue-400 hover:bg-blue-100"
          >
            예약하기
          </button>
        </div>
      </div>
    </div>
  );
}
