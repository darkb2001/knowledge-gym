import type { Metadata } from "next";
import localFont from "next/font/local";
import { LocaleProvider } from "@/components/locale";
import "./globals.css";

const geist = localFont({ src: "./fonts/GeistVF.woff", variable: "--font-geist", weight: "100 900", display: "swap" });
const geistMono = localFont({ src: "./fonts/GeistMonoVF.woff", variable: "--font-geist-mono", weight: "100 900", display: "swap" });

export const metadata: Metadata = {
  title: "Knowledge Gym",
  description: "Choose a topic, practise IT knowledge and build lasting understanding with Knowledge Gym.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi">
      <body className={`${geist.variable} ${geistMono.variable} min-h-[100dvh] antialiased`}><LocaleProvider>{children}</LocaleProvider></body>
    </html>
  );
}
