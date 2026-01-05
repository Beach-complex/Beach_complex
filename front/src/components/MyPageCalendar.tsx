import { useState, useEffect, useMemo } from 'react';
import { getSavedDates, removeSavedDate, setSavedDates as persistSavedDates, type SavedDate } from '../data/savedDates';
import { X, Trash2, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { deleteReservation, getMyReservations, ApiError as ReservationApiError } from '../api/reservations';
import { fetchBeaches } from '../api/beaches';

interface MyPageCalendarProps {
  selectedDate: Date | undefined;
  onDateSelect: (date: Date) => void;
  isAuthenticated?: boolean;
}

export function MyPageCalendar({ selectedDate, onDateSelect, isAuthenticated }: MyPageCalendarProps) {
  const [clickedDate, setClickedDate] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [savedDates, setSavedDates] = useState<SavedDate[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const toDateFromApi = (value: string | number) => {
    if (typeof value === 'number') {
      const epochMs = value < 1e12 ? value * 1000 : value;
      return new Date(epochMs);
    }
    const trimmed = value.trim();
    if (/^\d+(\.\d+)?$/.test(trimmed)) {
      const numeric = Number(trimmed);
      const epochMs = numeric < 1e12 ? numeric * 1000 : numeric;
      return new Date(epochMs);
    }
    return new Date(value);
  };
  
  const currentDate = selectedDate || new Date();
  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  
  const calendarDays = [];
  
  for (let i = 0; i < firstDay; i++) {
    calendarDays.push(null);
  }
  
  for (let day = 1; day <= daysInMonth; day++) {
    calendarDays.push(day);
  }

  useEffect(() => {
    let active = true;

    if (!isAuthenticated) {
      setSavedDates([]);
      setLoadError(null);
      return;
    }

    const loadReservations = async () => {
      setIsLoading(true);
      setLoadError(null);

      const localSavedDates = getSavedDates();
      if (localSavedDates.length > 0) {
        setSavedDates(localSavedDates);
      }

      try {
        const [reservationsResult, beachesResult] = await Promise.allSettled([
          getMyReservations(),
          fetchBeaches(),
        ]);

        if (!active) return;

        if (reservationsResult.status === 'rejected') {
          console.error('Failed to load reservations', reservationsResult.reason);
          const message =
            reservationsResult.reason instanceof ReservationApiError
              ? reservationsResult.reason.message || '?? ??? ???? ????.'
              : '?? ??? ???? ????.';
          setLoadError(message);
          setSavedDates(localSavedDates);
          return;
        }

        const beaches = beachesResult.status === 'fulfilled' ? beachesResult.value : [];
        if (beachesResult.status === 'rejected') {
          console.warn('Failed to load beaches for reservation labels', beachesResult.reason);
        }

        const beachNameById = new Map(beaches.map((beach) => [beach.id, beach.name]));
        const mapped: SavedDate[] = reservationsResult.value.map((reservation) => {
          const reservedAt = toDateFromApi(reservation.reservedAtUtc);
          return {
            id: reservation.reservationId,
            reservationId: reservation.reservationId,
            beachId: reservation.beachId,
            beachName: beachNameById.get(reservation.beachId) ?? '????',
            date: reservedAt,
            hour: reservedAt.getHours(),
            status: 'normal',
            createdAt: toDateFromApi(reservation.createdAtUtc),
          };
        });

        const sorted = mapped.sort((a, b) => {
          const timeDiff = a.date.getTime() - b.date.getTime();
          if (timeDiff != 0) return timeDiff;
          return a.hour - b.hour;
        });

        persistSavedDates(sorted);
        setSavedDates(sorted);
      } finally {
        if (active) setIsLoading(false);
      }
    };

    loadReservations();

    return () => {
      active = false;
    };
  }, [isAuthenticated, refreshKey]);

  const monthSavedDates = useMemo(
    () =>
      savedDates.filter((saved) => {
        const savedDate = new Date(saved.date);
        return savedDate.getFullYear() === year && savedDate.getMonth() === month;
      }),
    [savedDates, year, month],
  );

  const getSavedDatesForDay = (day: number) => {
    return monthSavedDates.filter(saved => {
      const savedDate = new Date(saved.date);
      return savedDate.getDate() === day;
    });
  };

  const handleDateClick = (day: number) => {
    const daySavedDates = getSavedDatesForDay(day);
    if (daySavedDates.length > 0) {
      setClickedDate(clickedDate === day ? null : day);
    }
  };

  const handleDeleteSavedDate = async (saved: SavedDate) => {
    const removeFromState = () => {
      removeSavedDate(saved.id);
      setSavedDates((prev) => {
        const next = prev.filter((item) => item.id !== saved.id);
        if (clickedDate !== null) {
          const hasRemaining = next.some((item) => {
            const savedDate = new Date(item.date);
            return savedDate.getDate() === clickedDate &&
              savedDate.getFullYear() === year &&
              savedDate.getMonth() === month;
          });
          if (!hasRemaining) {
            setClickedDate(null);
          }
        }
        return next;
      });
      toast.success('??? ?????.');

      // Refresh the calendar data
      setRefreshKey(prev => prev + 1);
    };

    if (!saved.reservationId || !saved.beachId) {
      removeFromState();
      return;
    }

    try {
      await deleteReservation(saved.beachId, saved.reservationId);
    } catch (error) {
      let message = '?? ??? ?????.';
      if (error instanceof ReservationApiError) {
        message = error.message || message;
      }
      toast.error(message);
      return;
    }

    removeFromState();
  };

  const getStatusColor = (status: 'free' | 'normal' | 'busy') => {
    switch (status) {
      case 'free':
        return '#51FF00';
      case 'normal':
        return '#FFEA00';
      case 'busy':
        return '#FF0000';
      default:
        return '#51FF00';
    }
  };

  const getStatusLabel = (status: 'free' | 'normal' | 'busy') => {
    switch (status) {
      case 'free':
        return '여유';
      case 'normal':
        return '보통';
      case 'busy':
        return '혼잡';
      default:
        return '여유';
    }
  };
  
  const isToday = (day: number) => {
    const today = new Date();
    return today.getDate() === day && 
           today.getMonth() === month && 
           today.getFullYear() === year;
  };
  
  const monthNames = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

  const handlePrevMonth = () => {
    const newDate = new Date(year, month - 1, 1);
    onDateSelect(newDate);
    setClickedDate(null);
  };

  const handleNextMonth = () => {
    const newDate = new Date(year, month + 1, 1);
    onDateSelect(newDate);
    setClickedDate(null);
  };

  return (
    <div key={refreshKey} className="bg-card dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-border">
      {isLoading && (
        <div className="mb-3 text-center text-[12px] text-muted-foreground">
          예약 정보를 불러오는 중...
        </div>
      )}
      {loadError && (
        <div className="mb-3 text-center text-[12px] text-red-500">
          {loadError}
        </div>
      )}
      <div className="flex items-center justify-between mb-6">
        <button
          onClick={handlePrevMonth}
          className="p-2 hover:bg-accent rounded-lg transition-colors"
          aria-label="이전 달"
        >
          <ChevronLeft className="w-5 h-5 text-foreground" />
        </button>
        
        <h2 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[24px] tracking-wider text-foreground">
          {monthNames[month]}
        </h2>
        
        <button
          onClick={handleNextMonth}
          className="p-2 hover:bg-accent rounded-lg transition-colors"
          aria-label="다음 달"
        >
          <ChevronRight className="w-5 h-5 text-foreground" />
        </button>
      </div>

      <div className="grid grid-cols-7 gap-2 mb-3">
        {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((day, idx) => (
          <div key={idx} className="text-center">
            <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-muted-foreground">
              {day}
            </span>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-2">
        {calendarDays.map((day, idx) => {
          if (day === null) {
            return <div key={`empty-${idx}`} className="aspect-square" />;
          }
          
          const daySavedDates = getSavedDatesForDay(day);
          const today = isToday(day);
          
            return (
              <button
                key={day}
                onClick={() => handleDateClick(day)}
                disabled={daySavedDates.length === 0}
                className={`aspect-square flex flex-col items-center justify-start p-1 rounded-lg relative transition-all ${
                  today 
                    ? 'bg-blue-600 text-white dark:bg-blue-500' 
                    : daySavedDates.length > 0 
                    ? 'bg-blue-50 dark:bg-blue-900/30 hover:bg-blue-100 dark:hover:bg-blue-900/50 cursor-pointer' 
                    : ''
                } ${clickedDate === day ? 'ring-2 ring-blue-500' : ''}`}
              >
                <span className={`font-['Noto_Sans_KR:Medium',_sans-serif] text-[14px] mb-1 ${
                  today ? 'text-white' : day === 7 || day === 14 || day === 21 || day === 28 ? 'text-red-500 dark:text-red-400' : 'text-foreground'
                }`}>
                  {day}
                </span>
                
                {daySavedDates.length > 0 && (
                  <div className="mt-0.5 w-full px-1 flex flex-col flex-1">
                    <div className="flex flex-col gap-0.5">
                      {daySavedDates.slice(0, 3).map((saved, savedIdx) => (
                        <div
                          key={savedIdx}
                          className="w-full h-1 rounded-full bg-blue-500 dark:bg-blue-400"
                          title={`${saved.beachName} ${saved.hour}:00`}
                        />
                      ))}
                    </div>
                    <div
                      className={`mt-auto min-h-[10px] text-[8px] leading-[10px] text-center ${
                        today ? 'text-white/90' : 'text-blue-600 dark:text-blue-400'
                      }`}
                    >
                      {daySavedDates.length > 3 ? `+${daySavedDates.length - 3}` : ''}
                    </div>
                  </div>
                )}
              </button>
            );
          })}
      </div>

      {/* Saved Dates Details Panel */}
      {clickedDate !== null && getSavedDatesForDay(clickedDate).length > 0 && (
        <div className="mt-4 p-4 bg-gradient-to-r from-blue-50 to-blue-100 dark:from-blue-900/30 dark:to-blue-800/30 rounded-lg border-l-4 border-blue-500 relative">
          <button
            onClick={() => setClickedDate(null)}
            className="absolute top-2 right-2 p-1 hover:bg-blue-200 dark:hover:bg-blue-700 rounded-full transition-colors"
          >
            <X className="w-4 h-4 text-foreground" />
          </button>
          
          <h4 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] mb-3 text-foreground">
            {month + 1}월 {clickedDate}일의 일정
          </h4>
          
          <div className="space-y-2">
            {getSavedDatesForDay(clickedDate).map((saved) => (
              <div 
                key={saved.id}
                className="flex items-center gap-3 p-3 bg-card dark:bg-gray-800 rounded-lg border border-border group hover:border-blue-500 transition-colors"
              >
                <div 
                  className="w-3 h-3 rounded-full shrink-0"
                  style={{ backgroundColor: getStatusColor(saved.status) }}
                  title={getStatusLabel(saved.status)}
                />
                <div className="flex-1">
                  <h5 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] mb-1 text-foreground">
                    {saved.beachName}
                  </h5>
                  <div className="flex items-center gap-2">
                    <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">
                      {saved.hour}:00
                    </p>
                    <span className="text-[10px] text-muted-foreground">•</span>
                    <p 
                      className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px]"
                      style={{ color: getStatusColor(saved.status) }}
                    >
                      {getStatusLabel(saved.status)}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => handleDeleteSavedDate(saved)}
                  className="p-2 hover:bg-red-100 dark:hover:bg-red-900/30 rounded-lg transition-colors opacity-100 sm:opacity-0 sm:group-hover:opacity-100"
                  title="일정 삭제"
                >
                  <Trash2 className="w-4 h-4 text-red-500 dark:text-red-400" />
                </button>
              </div>
            ))}
          </div>

          <p className="mt-3 text-[11px] text-muted-foreground text-center">
            일정을 눌러서 삭제할 수 있습니다
          </p>
        </div>
      )}
    </div>
  );
}
