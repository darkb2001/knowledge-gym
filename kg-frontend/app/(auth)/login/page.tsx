"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import {
  AuthShell,
  Field,
  ghostBtnClass,
  inputClass,
  primaryBtnClass,
} from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { login, startGoogleLogin } from "@/lib/auth";

export default function LoginPage() {
  const { t } = useLocale();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email.trim(), password);
      router.replace("/learn");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng nhập thất bại");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title={t("Đăng nhập")} subtitle={t("Tiếp tục buổi tập với tài khoản của bạn.")}>
      <form onSubmit={onSubmit} className="animate-fade-up-delay">
        <Field label={t("Email")}>
          <input
            className={inputClass}
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={t("Mật khẩu")}>
          <input
            className={inputClass}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        {error ? (
          <p className="mb-4 text-sm text-warning" role="alert">
            {t(error)}
          </p>
        ) : null}
        <button type="submit" className={primaryBtnClass} disabled={busy}>
          {busy ? t("Đang vào…") : t("Vào phòng tập")}
        </button>
      </form>

      <button type="button" className={`${ghostBtnClass} mt-3`} onClick={startGoogleLogin}>
        {t("Tiếp tục với Google")}</button>

      <div className="mt-6 flex flex-col gap-2 text-sm text-subtle">
        <Link href="/forgot-password" className="hover:text-warning">
          {t("Quên mật khẩu?")}</Link>
        <p>
          {t("Chưa có tài khoản?")}{" "}
          <Link href="/register" className="text-positive underline underline-offset-4 hover:text-strong">
            {t("Đăng ký")}</Link>
        </p>
      </div>
    </AuthShell>
  );
}
