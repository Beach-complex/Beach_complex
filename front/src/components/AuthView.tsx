import { useEffect, useState, type FormEvent } from "react";
import { ChevronLeft, Eye, EyeOff } from "lucide-react";

import { ApiError, logIn, signUp } from "../api/auth";
import { saveAuth, type StoredAuth } from "../utils/auth";
import { Button } from "./ui/button";
import { Card, CardContent } from "./ui/card";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { SignupEmailSentAfter } from "./SignupEmailSentView";

const PASSWORD_PATTERN =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]+$/;

// TODO(OAuth): Google/Kakao 등 OAuth 로그인 도입 시 Email/Password 폼과 분리(버튼/리다이렉트/콜백 처리 포함).
interface AuthViewProps {
  initialMode?: "login" | "signup";
  notice?: string | null;
  onClose: () => void;
  onAuthSuccess: (auth: StoredAuth) => void;
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

export function AuthView({
  initialMode = "login",
  notice,
  onClose,
  onAuthSuccess,
}: AuthViewProps) {
  const [mode, setMode] = useState<"login" | "signup">(initialMode);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formNotice, setFormNotice] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [showSignupPassword, setShowSignupPassword] = useState(false);
  const [showEmailSent, setShowEmailSent] = useState(false);
  const [signupEmail, setSignupEmail] = useState("");
  const [loginForm, setLoginForm] = useState({
    email: "",
    password: "",
  });
  const [signupForm, setSignupForm] = useState({
    name: "",
    email: "",
    password: "",
  });

  useEffect(() => {
    setMode(initialMode);
    setFormError(null);
    setFormNotice(null);
    setFieldErrors({});
  }, [initialMode]);

  const switchMode = (nextMode: "login" | "signup") => {
    setMode(nextMode);
    setFormError(null);
    setFormNotice(null);
    setFieldErrors({});
  };

  const validateLogin = () => {
    const errors: Record<string, string> = {};

    if (!loginForm.email.trim()) {
      errors.email = "이메일을 입력해 주세요.";
    }

    if (!loginForm.password) {
      errors.password = "비밀번호를 입력해 주세요.";
    }

    return errors;
  };

  const validateSignUp = () => {
    const errors: Record<string, string> = {};

    const trimmedName = signupForm.name.trim();

    if (!trimmedName) {
      errors.name = "이름을 입력해 주세요.";
    } else if (trimmedName.length < 2) {
      errors.name = "이름은 2자 이상이어야 합니다.";
    } else if (trimmedName.length > 100) {
      errors.name = "이름은 100자 이하로 입력해 주세요.";
    }

    if (!signupForm.email.trim()) {
      errors.email = "이메일을 입력해 주세요.";
    } else if (!/^\S+@\S+\.\S+$/.test(signupForm.email)) {
      errors.email = "올바른 이메일을 입력해 주세요.";
    }

    if (!signupForm.password) {
      errors.password = "비밀번호를 입력해 주세요.";
    } else if (signupForm.password.length < 8) {
      errors.password = "비밀번호는 8자 이상이어야 합니다.";
    } else if (!PASSWORD_PATTERN.test(signupForm.password)) {
      errors.password = "비밀번호는 대문자/소문자/숫자/특수문자를 포함해야 합니다.";
    }

    return errors;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);
    setFormNotice(null);
    setFieldErrors({});

    if (mode === "login") {
      const errors = validateLogin();
      if (Object.keys(errors).length > 0) {
        setFieldErrors(errors);
        return;
      }

      try {
        setIsSubmitting(true);
        const response = await logIn({
          email: loginForm.email.trim(),
          password: loginForm.password,
        });
        const stored = saveAuth(response);
        onAuthSuccess(stored);
      } catch (error) {
        if (error instanceof ApiError) {
          setFormError(error.message);
          setFieldErrors(error.fieldErrors ?? {});
        } else {
          setFormError("현재 로그인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
      } finally {
        setIsSubmitting(false);
      }
      return;
    }

    const errors = validateSignUp();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    try {
      setIsSubmitting(true);
      await signUp({
        name: signupForm.name.trim(),
        email: signupForm.email.trim(),
        password: signupForm.password,
      });

      // 이메일 인증 안내 화면 표시
      setSignupEmail(signupForm.email.trim());
      setShowEmailSent(true);
    } catch (error) {
      if (error instanceof ApiError) {
        setFormError(error.message);
        setFieldErrors(error.fieldErrors ?? {});
      } else {
        setFormError("현재 회원가입할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // 이메일 발송 안내 화면 표시
  if (showEmailSent) {
    return (
      <SignupEmailSentAfter
        email={signupEmail}
        onClose={() => {
          setShowEmailSent(false);
          setMode("login");
          setFormNotice("이메일 인증 후 로그인해 주세요.");
        }}
      />
    );
  }

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
          <WaveLogo />
          <div>
            <h1 className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[18px] text-foreground">
              비치체크
            </h1>
            <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[12px] text-muted-foreground">
              찜과 알림을 관리하려면 로그인하세요.
            </p>
          </div>
        </div>
      </div>

      <div className="-mt-8 px-4 pb-10">
        <Card className="shadow-sm border-border">
          <CardContent className="pt-6 space-y-5">
            <div className="grid grid-cols-2 gap-2 rounded-lg bg-muted p-1">
              <button
                type="button"
                onClick={() => switchMode("login")}
                className={`rounded-md px-3 py-2 text-[12px] font-medium transition ${
                  mode === "login"
                    ? "bg-card text-foreground shadow-sm"
                    : "text-muted-foreground"
                }`}
              >
                로그인
              </button>
              <button
                type="button"
                onClick={() => switchMode("signup")}
                className={`rounded-md px-3 py-2 text-[12px] font-medium transition ${
                  mode === "signup"
                    ? "bg-card text-foreground shadow-sm"
                    : "text-muted-foreground"
                }`}
              >
                회원가입
              </button>
            </div>

            {notice && (
              <div className="rounded-lg bg-amber-50 text-amber-700 text-xs px-3 py-2 border border-amber-100">
                {notice}
              </div>
            )}

            {formNotice && (
              <div className="rounded-lg bg-blue-50 text-blue-700 text-xs px-3 py-2 border border-blue-100">
                {formNotice}
              </div>
            )}

            {formError && (
              <div className="rounded-lg bg-red-100 text-red-600 text-xs px-3 py-2 border border-red-200">
                {formError}
              </div>
            )}

            <form className="space-y-4" onSubmit={handleSubmit}>
              {mode === "signup" && (
                <div className="space-y-1.5">
                  <Label htmlFor="signup-name" className="text-[12px]">
                    이름
                  </Label>
                  <Input
                    id="signup-name"
                    type="text"
                    autoComplete="name"
                    value={signupForm.name}
                    onChange={(event) =>
                      setSignupForm((prev) => ({ ...prev, name: event.target.value }))
                    }
                    aria-invalid={Boolean(fieldErrors.name)}
                    placeholder="이름을 입력하세요"
                  />
                  {fieldErrors.name && (
                    <p className="text-[11px] text-destructive">{fieldErrors.name}</p>
                  )}
                </div>
              )}

              <div className="space-y-1.5">
                <Label htmlFor={`${mode}-email`} className="text-[12px]">
                  이메일
                </Label>
                <Input
                  id={`${mode}-email`}
                  type="email"
                  autoComplete="email"
                  value={mode === "login" ? loginForm.email : signupForm.email}
                  onChange={(event) => {
                    const value = event.target.value;
                    if (mode === "login") {
                      setLoginForm((prev) => ({ ...prev, email: value }));
                    } else {
                      setSignupForm((prev) => ({ ...prev, email: value }));
                    }
                  }}
                  aria-invalid={Boolean(fieldErrors.email)}
                  placeholder="예: user@example.com"
                />
                {fieldErrors.email && (
                  <p className="text-[11px] text-destructive">{fieldErrors.email}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor={`${mode}-password`} className="text-[12px]">
                  비밀번호
                </Label>
                <div className="relative">
                  <Input
                    id={`${mode}-password`}
                    type={
                      mode === "login"
                        ? showLoginPassword
                          ? "text"
                          : "password"
                        : showSignupPassword
                          ? "text"
                          : "password"
                    }
                    autoComplete={mode === "login" ? "current-password" : "new-password"}
                    value={mode === "login" ? loginForm.password : signupForm.password}
                    onChange={(event) => {
                      const value = event.target.value;
                      if (mode === "login") {
                        setLoginForm((prev) => ({ ...prev, password: value }));
                      } else {
                        setSignupForm((prev) => ({ ...prev, password: value }));
                      }
                    }}
                    aria-invalid={Boolean(fieldErrors.password)}
                    placeholder={mode === "login" ? "비밀번호를 입력하세요" : "비밀번호를 설정하세요"}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground"
                    onClick={() =>
                      mode === "login"
                        ? setShowLoginPassword((prev) => !prev)
                        : setShowSignupPassword((prev) => !prev)
                    }
                    aria-label={mode === "login" ? "비밀번호 보기 전환" : "비밀번호 보기 전환"}
                  >
                    {mode === "login" ? (
                      showLoginPassword ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )
                    ) : showSignupPassword ? (
                      <EyeOff className="w-4 h-4" />
                    ) : (
                      <Eye className="w-4 h-4" />
                    )}
                  </button>
                </div>
                {fieldErrors.password && (
                  <p className="text-[11px] text-destructive">{fieldErrors.password}</p>
                )}
                {mode === "signup" && !fieldErrors.password && (
                  <p className="text-[11px] text-muted-foreground">
                    8자 이상, 대/소문자, 숫자, 특수문자를 포함해 주세요.
                  </p>
                )}
              </div>

              <Button
                type="submit"
                className="w-full bg-[#007DFC] text-white hover:bg-[#0067d1]"
                disabled={isSubmitting}
              >
                {isSubmitting
                  ? mode === "login"
                    ? "로그인 중..."
                    : "회원가입 중..."
                  : mode === "login"
                    ? "로그인"
                    : "회원가입"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
