import { useState, useEffect } from "react";
import { resendVerification, ApiError } from "../api/auth";

interface Props {
  email: string;
}

export function ResendEmailButton({ email }: Props) {
  const [isResending, setIsResending] = useState(false);
  const [cooldownSeconds, setCooldownSeconds] = useState(0);
  const [message, setMessage] = useState<string | null>(null);

  // 쿨다운 타이머
  useEffect(() => {
    if (cooldownSeconds > 0) {
      const timer = setTimeout(() => {
        setCooldownSeconds(cooldownSeconds - 1);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldownSeconds]);

  // 페이지 로드 시 localStorage에서 마지막 재전송 시각 확인
  useEffect(() => {
    const lastSentAt = localStorage.getItem(`email_resend_${email}`);
    if (lastSentAt) {
      const elapsedSeconds = Math.floor((Date.now() - parseInt(lastSentAt)) / 1000);
      const remainingSeconds = 180 - elapsedSeconds; // 3분 = 180초
      if (remainingSeconds > 0) {
        setCooldownSeconds(remainingSeconds);
      }
    }
  }, [email]);

  const handleResend = async () => {
    setIsResending(true);
    setMessage(null);

    try {
      await resendVerification({ email });

      // 성공: 쿨다운 시작
      setCooldownSeconds(180); // 3분
      localStorage.setItem(`email_resend_${email}`, Date.now().toString());
      setMessage('인증 이메일을 다시 발송했습니다. 잠시 후 확인해주세요.');

    } catch (error) {
      if (error instanceof ApiError) {
        const status = error.status;
        const errorMessage = error.message;

        if (status === 400 && errorMessage?.includes('최근에 발송되었습니다')) {
          // 쿨다운 위반
          setMessage('인증 이메일이 최근에 발송되었습니다. 잠시 후 다시 시도해주세요.');
          setCooldownSeconds(180); // 3분 강제 설정
        } else {
          setMessage('이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.');
        }
      } else {
        setMessage('네트워크 오류가 발생했습니다.');
      }
    } finally {
      setIsResending(false);
    }
  };

  const isDisabled = isResending || cooldownSeconds > 0;

  return (
    <div className="space-y-3">
      <div className="text-center">
        <p className="text-[12px] text-muted-foreground mb-3">
          이메일이 오지 않나요?
        </p>

        <button
          onClick={handleResend}
          disabled={isDisabled}
          className={`
            w-full px-4 py-2.5 rounded-md text-[13px] font-medium
            transition-colors duration-200
            ${
              isDisabled
                ? 'bg-gray-100 text-gray-400 cursor-not-allowed dark:bg-gray-800 dark:text-gray-600'
                : 'bg-blue-50 text-blue-600 hover:bg-blue-100 dark:bg-blue-950 dark:text-blue-400 dark:hover:bg-blue-900'
            }
          `}
          aria-label={
            isResending
              ? '이메일 발송 중입니다'
              : cooldownSeconds > 0
              ? `${Math.floor(cooldownSeconds / 60)}분 ${cooldownSeconds % 60}초 후 재전송 가능`
              : '인증 이메일 재전송'
          }
          aria-busy={isResending}
        >
          {isResending ? (
            <span className="flex items-center justify-center gap-2">
              <span className="inline-block w-4 h-4 border-2 border-blue-600 border-t-transparent rounded-full animate-spin" />
              발송 중...
            </span>
          ) : cooldownSeconds > 0 ? (
            `재전송 가능 (${Math.floor(cooldownSeconds / 60)}분 ${cooldownSeconds % 60}초 후)`
          ) : (
            '인증 이메일 재전송'
          )}
        </button>
      </div>

      {message && (
        <div
          className={`
            rounded-lg px-3 py-2 text-[12px] border
            ${
              message.includes('실패') || message.includes('오류')
                ? 'bg-red-50 text-red-700 border-red-100 dark:bg-red-950 dark:text-red-300 dark:border-red-900'
                : 'bg-green-50 text-green-700 border-green-100 dark:bg-green-950 dark:text-green-300 dark:border-green-900'
            }
          `}
          role="alert"
          aria-live="assertive"
        >
          {message}
        </div>
      )}
    </div>
  );
}