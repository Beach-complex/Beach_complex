import { ResendEmailButton } from "@/components/ResendEmailButton";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ChevronLeft, Mail } from "lucide-react";

interface SignupEmailSentAfterProps {
  email: string;
  onClose?: () => void;
}

function MailIcon() {
  return (
    <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
      <circle cx="20" cy="20" r="20" fill="#007DFC" />
      <path
        d="M12 15L20 20L28 15M12 15V25C12 25.5523 12.4477 26 13 26H27C27.5523 26 28 25.5523 28 25V15M12 15L13 14H27L28 15"
        stroke="white"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function SignupEmailSentAfter({ email, onClose }: SignupEmailSentAfterProps) {
  return (
    <div className="relative min-h-screen bg-background max-w-[480px] mx-auto">
      <div className="relative bg-gradient-to-b from-[#E8F4FF] to-[#F5F5F5] dark:from-gray-900 dark:to-gray-800 p-4 pb-10">
        <button
          onClick={onClose}
          className="flex items-center gap-1 text-[12px] text-muted-foreground"
        >
          <ChevronLeft className="w-4 h-4" />
          뒤로
        </button>

        <div className="mt-4 flex items-center gap-3">
          <MailIcon />
          <div>
            <h1 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[18px] text-foreground">
              인증 이메일 발송
            </h1>
            <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[12px] text-muted-foreground">
              이메일을 확인하여 인증을 완료하세요.
            </p>
          </div>
        </div>
      </div>

      <div className="-mt-8 px-4 pb-10">
        <Card className="shadow-sm border-border">
          <CardContent className="pt-6 space-y-5">
            <div className="text-center space-y-3">
              <div className="w-16 h-16 mx-auto rounded-full bg-green-100 dark:bg-green-900 flex items-center justify-center">
                <Mail className="w-8 h-8 text-green-600 dark:text-green-400" />
              </div>

              <h2 className="text-[16px] font-semibold text-foreground">
                인증 이메일을 발송했습니다
              </h2>

              <p className="text-[13px] text-muted-foreground">
                <strong className="text-foreground">{email}</strong>
                <br />
                으로 인증 이메일을 발송했습니다.
              </p>
            </div>

            <div className="space-y-2 bg-blue-50 dark:bg-blue-950 rounded-lg p-4 border border-blue-100 dark:border-blue-900">
              <p className="text-[12px] text-blue-700 dark:text-blue-300 flex items-center gap-2">
                <span>📬</span>
                <span>이메일이 곧 도착합니다. (보통 1-2분 이내)</span>
              </p>
              <p className="text-[12px] text-amber-700 dark:text-amber-300 flex items-center gap-2">
                <span>⚠️</span>
                <span>스팸 폴더에 있을 수 있습니다.</span>
              </p>
            </div>

            <ResendEmailButton email={email} />

            {onClose && (
              <Button
                onClick={onClose}
                className="w-full bg-[#007DFC] text-white hover:bg-[#0067d1]"
              >
                확인
              </Button>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}