import { useState, useEffect } from 'react';
import { Activity, Bell, CheckCircle, XCircle } from 'lucide-react';
import { LineChart, Line, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { BottomNavigation } from './BottomNavigation';
import type { UserResponseDto } from '../types/auth';
import { getMessaging, onMessage } from 'firebase/messaging';

interface DeveloperModeViewProps {
  onNavigate: (view: string) => void;
  authUser?: UserResponseDto | null;
  accessToken?: string | null;
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

// Mock data for weekly users over 6 months (24 weeks)
const weeklyUsersData = [
  { week: '1주', users: 1200 },
  { week: '2주', users: 1350 },
  { week: '3주', users: 1280 },
  { week: '4주', users: 1450 },
  { week: '5주', users: 1520 },
  { week: '6주', users: 1680 },
  { week: '7주', users: 1590 },
  { week: '8주', users: 1720 },
  { week: '9주', users: 1650 },
  { week: '10주', users: 1890 },
  { week: '11주', users: 1940 },
  { week: '12주', users: 2100 },
  { week: '13주', users: 2050 },
  { week: '14주', users: 2180 },
  { week: '15주', users: 2220 },
  { week: '16주', users: 2350 },
  { week: '17주', users: 2280 },
  { week: '18주', users: 2450 },
  { week: '19주', users: 2520 },
  { week: '20주', users: 2680 },
  { week: '21주', users: 2590 },
  { week: '22주', users: 2720 },
  { week: '23주', users: 2650 },
  { week: '24주', users: 2800 },
];

// Mock data for hourly congestion
const hourlyCongestionData = [
  { hour: '00', value: 10 },
  { hour: '03', value: 8 },
  { hour: '06', value: 15 },
  { hour: '09', value: 45 },
  { hour: '12', value: 80 },
  { hour: '15', value: 95 },
  { hour: '18', value: 70 },
  { hour: '21', value: 35 },
];

// Mock data for model metrics
const modelMetrics = {
  precision: 0.92,
  recall: 0.89,
  f1Score: 0.90,
};

// Mock data for page loading times
const loadingTimesData = [
  { screen: '메인 화면', time: '0.8s', status: 'good' },
  { screen: '지도 상세 화면', time: '1.2s', status: 'good' },
  { screen: '주변 행사', time: '0.9s', status: 'good' },
  { screen: '마이페이지', time: '0.7s', status: 'good' },
  { screen: '개발자 모드', time: '1.0s', status: 'good' },
];

// Mock data for API status
const apiStatusData = [
  { name: '혼잡도 API', status: 'active', lastCheck: '방금 전' },
  { name: '날씨 API', status: 'active', lastCheck: '1분 전' },
  { name: '지도 API', status: 'active', lastCheck: '방금 전' },
  { name: '행사 정보 API', status: 'active', lastCheck: '2분 전' },
  { name: '알림 서비스', status: 'active', lastCheck: '방금 전' },
];

// Generate heatmap data (12 features x 24 hours)
const heatmapData = Array.from({ length: 12 }, (_, featureIdx) => 
  Array.from({ length: 24 }, (_, hourIdx) => {
    // Create patterns based on feature and time
    const baseValue = Math.random() * 0.3;
    
    // Weather features - higher during day
    if (featureIdx < 3) {
      return hourIdx >= 6 && hourIdx <= 18 ? 0.6 + Math.random() * 0.4 : baseValue;
    }
    // Day of week features
    if (featureIdx >= 3 && featureIdx < 5) {
      return 0.3 + Math.random() * 0.5;
    }
    // Time features - specific patterns
    if (featureIdx === 5) { // 오전
      return hourIdx >= 6 && hourIdx < 12 ? 0.7 + Math.random() * 0.3 : baseValue;
    }
    if (featureIdx === 6) { // 오후
      return hourIdx >= 12 && hourIdx < 18 ? 0.7 + Math.random() * 0.3 : baseValue;
    }
    // Event features
    if (featureIdx >= 7 && featureIdx < 9) {
      return hourIdx >= 10 && hourIdx <= 20 ? 0.5 + Math.random() * 0.4 : baseValue;
    }
    // Population density - peaks afternoon
    if (featureIdx === 9) {
      return hourIdx >= 11 && hourIdx <= 16 ? 0.8 + Math.random() * 0.2 : baseValue + Math.random() * 0.3;
    }
    // Parking availability - inverse of population
    if (featureIdx === 10) {
      return hourIdx >= 11 && hourIdx <= 16 ? baseValue : 0.6 + Math.random() * 0.3;
    }
    // Wave height - random with slight pattern
    return 0.2 + Math.random() * 0.6;
  })
);

// Function to get color based on value (0-1)
const getHeatmapColor = (value: number): string => {
  // Blue (low) to White (mid) to Red (high)
  if (value < 0.5) {
    const intensity = value * 2;
    const r = Math.round(100 + (255 - 100) * intensity);
    const g = Math.round(150 + (255 - 150) * intensity);
    const b = 255;
    return `rgb(${r}, ${g}, ${b})`;
  } else {
    const intensity = (value - 0.5) * 2;
    const r = 255;
    const g = Math.round(255 - 100 * intensity);
    const b = Math.round(255 - 100 * intensity);
    return `rgb(${r}, ${g}, ${b})`;
  }
};

export function DeveloperModeView({ onNavigate, authUser, accessToken }: DeveloperModeViewProps) {
  const [activeTab, setActiveTab] = useState('mypage');
  const [testNotificationLoading, setTestNotificationLoading] = useState(false);
  const [testNotificationSuccess, setTestNotificationSuccess] = useState<boolean | null>(null);

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    if (tab === 'home') {
      onNavigate('main');
    } else if (tab === 'search') {
      onNavigate('main');
    } else if (tab === 'events') {
      onNavigate('events');
    } else if (tab === 'mypage') {
      onNavigate('mypage');
    }
  };

  // 포그라운드 FCM 메시지 리스너
  useEffect(() => {
    try {
      const messaging = getMessaging();

      // 포그라운드 메시지 수신 리스너 등록
      const unsubscribe = onMessage(messaging, (payload) => {
        console.log('[DeveloperMode] 포그라운드 메시지 수신:', payload);

        // 브라우저 알림 표시
        if (Notification.permission === 'granted') {
          const originalTitle = payload.notification?.title || '새 알림';
          const notificationTitle = `[포그라운드] ${originalTitle}`;
          const notificationOptions = {
            body: payload.notification?.body || '',
            icon: '/assets/icons/icon-192x192.png',
            badge: '/assets/icons/badge-72x72.png',
            tag: 'beach-notification-foreground',
            data: payload.data
          };

          new Notification(notificationTitle, notificationOptions);
        }

        // 성공 메시지 표시
        setTestNotificationSuccess(true);
        setTimeout(() => setTestNotificationSuccess(null), 5000);
      });

      // 컴포넌트 언마운트 시 리스너 제거
      return () => {
        unsubscribe();
      };
    } catch (error) {
      console.error('[DeveloperMode] 포그라운드 메시지 리스너 등록 실패:', error);
    }
  }, []);

  return (
    <div className="relative min-h-screen bg-background pb-20">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-gradient-to-b from-[#E8F4FF] to-[#F5F5F5] dark:from-gray-900 dark:to-gray-800 p-3 pb-3 shadow-sm">
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
              <h1 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[15px] leading-tight text-foreground whitespace-nowrap">개발자 모드</h1>
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] leading-tight text-muted-foreground whitespace-nowrap">
                시스템 모니터링 및 분석
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-4 space-y-4">
        {/* Model Metrics Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-4 text-foreground">
            모델 지표
          </h3>
          
          <div className="space-y-4">
            {/* Precision */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[13px] text-foreground">
                  Precision
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-blue-600 dark:text-blue-400">
                  0.92
                </span>
              </div>
              <div className="w-full h-2 bg-muted dark:bg-gray-700 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-blue-500 to-blue-400 rounded-full transition-all"
                  style={{ width: '92%' }}
                />
              </div>
            </div>

            {/* Recall */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[13px] text-foreground">
                  Recall
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-blue-600 dark:text-blue-400">
                  0.89
                </span>
              </div>
              <div className="w-full h-2 bg-muted dark:bg-gray-700 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-green-500 to-green-400 rounded-full transition-all"
                  style={{ width: '89%' }}
                />
              </div>
            </div>

            {/* F1 Score */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[13px] text-foreground">
                  F1 Score
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-blue-600 dark:text-blue-400">
                  0.90
                </span>
              </div>
              <div className="w-full h-2 bg-muted dark:bg-gray-700 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-purple-500 to-purple-400 rounded-full transition-all"
                  style={{ width: '90%' }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Section Reliability Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">
            구간별 신뢰성
          </h3>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
            시간대별 성능 분석
          </p>
          
          <div className="space-y-3">
            {/* Morning Performance */}
            <div className="bg-muted dark:bg-gray-700 rounded-lg p-3">
              <div className="flex items-center justify-between mb-1">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                  오전 (06:00-12:00)
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px] text-green-600 dark:text-green-400">
                  94%
                </span>
              </div>
              <div className="w-full h-1.5 bg-background dark:bg-gray-600 rounded-full overflow-hidden">
                <div className="h-full bg-green-500 rounded-full" style={{ width: '94%' }} />
              </div>
            </div>

            {/* Afternoon Performance */}
            <div className="bg-muted dark:bg-gray-700 rounded-lg p-3">
              <div className="flex items-center justify-between mb-1">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                  오후 (12:00-18:00)
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px] text-yellow-600 dark:text-yellow-400">
                  87%
                </span>
              </div>
              <div className="w-full h-1.5 bg-background dark:bg-gray-600 rounded-full overflow-hidden">
                <div className="h-full bg-yellow-500 rounded-full" style={{ width: '87%' }} />
              </div>
            </div>

            {/* Weekend Performance */}
            <div className="bg-muted dark:bg-gray-700 rounded-lg p-3">
              <div className="flex items-center justify-between mb-1">
                <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                  주말
                </span>
                <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px] text-orange-600 dark:text-orange-400">
                  82%
                </span>
              </div>
              <div className="w-full h-1.5 bg-background dark:bg-gray-600 rounded-full overflow-hidden">
                <div className="h-full bg-orange-500 rounded-full" style={{ width: '82%' }} />
              </div>
            </div>
          </div>

          {/* Feature Contributions */}
          <div className="mt-4 pt-4 border-t border-border">
            <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-3">
              피처 기여도
            </p>
            <div className="grid grid-cols-3 gap-2">
              <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-2 text-center">
                <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                  날씨
                </p>
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-blue-600 dark:text-blue-400">
                  38%
                </p>
              </div>
              <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-2 text-center">
                <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                  요일
                </p>
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-green-600 dark:text-green-400">
                  29%
                </p>
              </div>
              <div className="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-2 text-center">
                <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                  이벤트
                </p>
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[13px] text-purple-600 dark:text-purple-400">
                  33%
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Feature Heatmap Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">
            피처별 히트맵
          </h3>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
            시간대별 피처 중요도 분석
          </p>
          
          <div className="bg-muted dark:bg-gray-700 rounded-lg p-4 overflow-x-auto">
            <div className="min-w-[500px]">
              {/* Heatmap Grid */}
              <div className="flex">
                {/* Y-axis labels (Features) */}
                <div className="flex flex-col justify-between pr-3 py-6">
                  {[
                    '날씨_온도',
                    '날씨_습도',
                    '날씨_풍속',
                    '요일_주중',
                    '요일_주말',
                    '시간_오전',
                    '시간_오후',
                    '이벤트_축제',
                    '이벤트_공휴일',
                    '인구밀도',
                    '주차여유',
                    '파고',
                  ].map((feature, idx) => (
                    <div key={idx} className="h-6 flex items-center">
                      <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[9px] text-foreground whitespace-nowrap">
                        {feature}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Heatmap cells */}
                <div className="flex-1">
                  {/* X-axis labels (Hours) */}
                  <div className="flex mb-1">
                    {Array.from({ length: 24 }, (_, i) => i).map((hour) => (
                      <div key={hour} className="flex-1 text-center">
                        <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[8px] text-muted-foreground">
                          {hour}h
                        </span>
                      </div>
                    ))}
                  </div>

                  {/* Heatmap rows */}
                  <div className="space-y-1">
                    {heatmapData.map((row, rowIdx) => (
                      <div key={rowIdx} className="flex gap-1">
                        {row.map((value, colIdx) => {
                          const intensity = value;
                          const color = getHeatmapColor(intensity);
                          return (
                            <div
                              key={colIdx}
                              className="flex-1 h-6 rounded-sm transition-all hover:ring-1 hover:ring-blue-500 cursor-pointer"
                              style={{ backgroundColor: color }}
                              title={`${intensity.toFixed(2)}`}
                            />
                          );
                        })}
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* Legend */}
              <div className="flex items-center justify-center gap-2 mt-4 pt-4 border-t border-border">
                <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[9px] text-muted-foreground">
                  낮음
                </span>
                <div className="flex gap-0.5">
                  {[0, 0.2, 0.4, 0.6, 0.8, 1].map((val, idx) => (
                    <div
                      key={idx}
                      className="w-8 h-4 rounded-sm"
                      style={{ backgroundColor: getHeatmapColor(val) }}
                    />
                  ))}
                </div>
                <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[9px] text-muted-foreground">
                  높음
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Weekly Users Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">
            주간 접속자 수
          </h3>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
            최근 6개월간 주별 사용자 통계
          </p>
          
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                data={weeklyUsersData}
                margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
              >
                <defs>
                  <linearGradient id="colorUsers" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#007DFC" stopOpacity={0.8}/>
                    <stop offset="95%" stopColor="#007DFC" stopOpacity={0.1}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(128, 128, 128, 0.1)" />
                <XAxis 
                  dataKey="week" 
                  tick={{ fontSize: 10, fill: 'currentColor' }}
                  stroke="currentColor"
                  opacity={0.5}
                  interval={3}
                />
                <YAxis 
                  tick={{ fontSize: 10, fill: 'currentColor' }}
                  stroke="currentColor"
                  opacity={0.5}
                  width={40}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'var(--card)',
                    border: '1px solid var(--border)',
                    borderRadius: '8px',
                    fontSize: '12px',
                  }}
                  labelStyle={{ color: 'var(--foreground)' }}
                  itemStyle={{ color: '#007DFC' }}
                />
                <Area 
                  type="monotone" 
                  dataKey="users" 
                  stroke="#007DFC" 
                  strokeWidth={2}
                  fill="url(#colorUsers)"
                  name="접속자"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          
          <div className="mt-4 pt-4 border-t border-border grid grid-cols-3 gap-3">
            <div className="text-center">
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                평균
              </p>
              <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-foreground">
                {Math.round(weeklyUsersData.reduce((acc, curr) => acc + curr.users, 0) / weeklyUsersData.length).toLocaleString()}
              </p>
            </div>
            <div className="text-center">
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                최대
              </p>
              <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-blue-600 dark:text-blue-400">
                {Math.max(...weeklyUsersData.map(d => d.users)).toLocaleString()}
              </p>
            </div>
            <div className="text-center">
              <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[10px] text-muted-foreground mb-1">
                증가율
              </p>
              <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-green-600 dark:text-green-400">
                +{Math.round(((weeklyUsersData[weeklyUsersData.length - 1].users - weeklyUsersData[0].users) / weeklyUsersData[0].users) * 100)}%
              </p>
            </div>
          </div>
        </div>

        {/* Page Loading Times Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">
            화면별 로딩 시간
          </h3>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
            평균 페이지 로드 시간
          </p>
          
          <div className="space-y-4">
            {loadingTimesData.map((screen, idx) => {
              const timeValue = parseFloat(screen.time);
              const maxTime = 1.5;
              const widthPercent = (timeValue / maxTime) * 100;
              
              return (
                <div key={idx}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[12px] text-foreground">
                      {screen.screen}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px] text-blue-600 dark:text-blue-400">
                        {screen.time}
                      </span>
                      <Activity className="w-4 h-4 text-green-500" />
                    </div>
                  </div>
                  <div className="w-full h-2 bg-muted dark:bg-gray-700 rounded-full overflow-hidden">
                    <div 
                      className={`h-full rounded-full transition-all ${
                        timeValue < 1 
                          ? 'bg-gradient-to-r from-green-500 to-green-400' 
                          : timeValue < 1.2 
                          ? 'bg-gradient-to-r from-blue-500 to-blue-400'
                          : 'bg-gradient-to-r from-yellow-500 to-yellow-400'
                      }`}
                      style={{ width: `${Math.min(widthPercent, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* API Status Section */}
        <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
          <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] mb-3 text-foreground">
            API 상태 모니터링
          </h3>
          <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
            실시간 API 상태 확인
          </p>

          <div className="space-y-3">
            {apiStatusData.map((api, idx) => (
              <div key={idx} className="flex items-center justify-between bg-muted dark:bg-gray-700 rounded-lg p-3">
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                  <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                    {api.name}
                  </span>
                </div>
                <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground">
                  {api.lastCheck}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Notification Test Section */}
        {authUser && (
          <div className="bg-card dark:bg-gray-800 rounded-xl p-4 border border-border shadow-sm">
            <div className="flex items-center gap-2 mb-3">
              <Bell className="w-5 h-5 text-[#007DFC]" />
              <h3 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-foreground">
                알림 테스트
              </h3>
            </div>
            <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground mb-4">
              FCM 토큰 상태 확인 및 테스트 알림 발송
            </p>

            {/* Test Notification Button */}
            <button
              onClick={async () => {
                if (!accessToken) {
                  alert('로그인이 필요합니다.');
                  return;
                }

                setTestNotificationLoading(true);
                setTestNotificationSuccess(null);

                try {
                  const response = await fetch('/api/notifications/test', {
                    method: 'POST',
                    headers: {
                      // TODO(OAuth): tokenType이 Bearer 고정이 아닐 수 있으므로 저장된 tokenType 기반으로 Authorization 헤더 구성.
                      'Authorization': `Bearer ${accessToken}`,
                    },
                  });

                  if (!response.ok) {
                    if (response.status === 400) {
                      alert('FCM 토큰이 등록되지 않았습니다. 먼저 알림 권한을 허용해주세요.');
                      setTestNotificationSuccess(false);
                    } else {
                      alert('테스트 알림 발송에 실패했습니다.');
                      setTestNotificationSuccess(false);
                    }
                    return;
                  }

                  // 202 Accepted - 요청 접수됨
                  setTestNotificationSuccess(true);
                } catch (error) {
                  console.error('테스트 알림 요청 실패:', error);
                  alert('테스트 알림 요청 중 오류가 발생했습니다.');
                  setTestNotificationSuccess(false);
                } finally {
                  setTestNotificationLoading(false);
                }
              }}
              disabled={testNotificationLoading}
              className="w-full mb-4 p-3 bg-[#007DFC] text-white rounded-lg font-['Noto_Sans_KR:Medium',_sans-serif] text-[13px] hover:bg-[#0067d1] transition shadow-sm disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {testNotificationLoading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  발송 요청 중...
                </>
              ) : (
                <>
                  <Bell className="w-4 h-4" />
                  테스트 알림 발송
                </>
              )}
            </button>

            {/* Test Result */}
            {testNotificationSuccess !== null && (
              <div
                className={`p-3 rounded-lg border ${
                  testNotificationSuccess
                    ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800'
                    : 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800'
                }`}
              >
                <div className="flex items-start gap-2">
                  {testNotificationSuccess ? (
                    <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
                  ) : (
                    <XCircle className="w-5 h-5 text-red-600 dark:text-red-400 flex-shrink-0 mt-0.5" />
                  )}
                  <div className="flex-1">
                    <p
                      className={`font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] ${
                        testNotificationSuccess
                          ? 'text-green-800 dark:text-green-300'
                          : 'text-red-800 dark:text-red-300'
                      }`}
                    >
                      {testNotificationSuccess
                        ? '테스트 알림 발송 요청이 접수되었습니다. 잠시 후 알림을 확인해주세요.'
                        : '테스트 알림 발송에 실패했습니다.'}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* Notification Permission Status */}
            <div className="mt-4 pt-4 border-t border-border">
              <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground mb-2">
                브라우저 알림 권한 상태
              </p>
              <div className="flex items-center gap-2">
                <div
                  className={`w-2 h-2 rounded-full ${
                    typeof Notification !== 'undefined' && Notification.permission === 'granted'
                      ? 'bg-green-500'
                      : Notification.permission === 'denied'
                      ? 'bg-red-500'
                      : 'bg-yellow-500'
                  }`}
                />
                <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[12px] text-foreground">
                  {typeof Notification !== 'undefined'
                    ? Notification.permission === 'granted'
                      ? '허용됨'
                      : Notification.permission === 'denied'
                      ? '거부됨'
                      : '미설정'
                    : '지원 안 됨'}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Bottom Navigation */}
      <BottomNavigation activeTab={activeTab} onTabChange={handleTabChange} />
    </div>
  );
}
