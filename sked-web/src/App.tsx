import React, { useState } from 'react';
import LiquidMetalHero from '@/components/ui/liquid-metal-hero';
import { Navbar } from '@/components/Navbar';
import { InstallSteps } from '@/components/InstallSteps';
import { FeatureShowcase } from '@/components/FeatureShowcase';
import { LiveMockup } from '@/components/LiveMockup';
import { WidgetPreview } from '@/components/WidgetPreview';
import { DeveloperCard } from '@/components/DeveloperCard';
import { Footer } from '@/components/Footer';

export default function App() {
  const [platform, setPlatform] = useState<'android' | 'ios'>('android');

  const scrollToInstall = (targetPlatform: 'android' | 'ios' = 'android') => {
    setPlatform(targetPlatform);
    const el = document.getElementById('install');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleDownloadAndroid = () => {
    scrollToInstall('android');
  };

  const handleDownloadIos = () => {
    scrollToInstall('ios');
  };

  return (
    <div className="min-h-screen bg-[#0A0A0A] text-[#E8E6E3] font-['Inter'] relative selection:bg-[#FF6B1A] selection:text-[#0A0A0A]">
      {/* Navigation */}
      <Navbar onInstallClick={scrollToInstall} />

      {/* Liquid Metal Hero Section */}
      <div className="pt-16">
        <LiquidMetalHero
          badge="✨ SKED FOR LPU • ANDROID & iOS COMPANION"
          title="Never Miss A Class Again"
          subtitle="Real-time class schedule, live attendance sync, smart attendance insights, and pure relaxation on Sundays. 100% on-device, zero SaaS clutter, zero tracking."
          primaryCtaLabel={
            <span className="flex items-center gap-2">
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor"><path d="M17.523 2.262a.758.758 0 0 0-1.037.263l-1.1 1.907A6.6 6.6 0 0 0 12.012 3.5c-1.2 0-2.326.321-3.296.881L7.59 2.46a.758.758 0 1 0-1.312.762l1.07 1.852A6.62 6.62 0 0 0 5.4 9.5h13.2a6.62 6.62 0 0 0-1.894-4.478l1.08-1.873a.758.758 0 0 0-.263-1.037M9.5 7.5a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5m5 0a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5M5.25 10.5A1.25 1.25 0 0 0 4 11.75v4.5a1.25 1.25 0 0 0 2.5 0v-4.5a1.25 1.25 0 0 0-1.25-1.25m13.5 0A1.25 1.25 0 0 0 17.5 11.75v4.5a1.25 1.25 0 0 0 2.5 0v-4.5a1.25 1.25 0 0 0-1.25-1.25M5.5 11v6.5A2 2 0 0 0 7.5 19.5h1v2.75a1.25 1.25 0 0 0 2.5 0V19.5h2v2.75a1.25 1.25 0 0 0 2.5 0V19.5h1a2 2 0 0 0 2-2V11z"/></svg>
              Download for Android
            </span>
          }
          secondaryCtaLabel={
            <span className="flex items-center gap-2">
              <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
              Download for iOS (.IPA)
            </span>
          }
          onPrimaryCtaClick={handleDownloadAndroid}
          onSecondaryCtaClick={handleDownloadIos}
          features={[
            "100% On-Device & Private",
            "15-Min Background Glance Sync",
            "Sunday Bitmoji Chill Mode"
          ]}
        />
      </div>

      {/* Interactive Interface Preview */}
      <LiveMockup />

      {/* Home Screen Widget Preview */}
      <WidgetPreview />

      {/* Installation Hub (Android & iOS) */}
      <InstallSteps
        currentPlatform={platform}
        onPlatformChange={setPlatform}
      />

      {/* Feature Deep Dive */}
      <FeatureShowcase />

      {/* About the Developer */}
      <DeveloperCard />

      {/* Footer */}
      <Footer />
    </div>
  );
}
