import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { 
  Download, 
  CheckCircle2, 
  Terminal, 
  Copy, 
  Check, 
  ShieldCheck, 
  Sparkles,
  AlertCircle
} from 'lucide-react';

const AndroidIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor"><path d="M17.523 2.262a.758.758 0 0 0-1.037.263l-1.1 1.907A6.6 6.6 0 0 0 12.012 3.5c-1.2 0-2.326.321-3.296.881L7.59 2.46a.758.758 0 1 0-1.312.762l1.07 1.852A6.62 6.62 0 0 0 5.4 9.5h13.2a6.62 6.62 0 0 0-1.894-4.478l1.08-1.873a.758.758 0 0 0-.263-1.037M9.5 7.5a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5m5 0a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5M5.25 10.5A1.25 1.25 0 0 0 4 11.75v4.5a1.25 1.25 0 0 0 2.5 0v-4.5a1.25 1.25 0 0 0-1.25-1.25m13.5 0A1.25 1.25 0 0 0 17.5 11.75v4.5a1.25 1.25 0 0 0 2.5 0v-4.5a1.25 1.25 0 0 0-1.25-1.25M5.5 11v6.5A2 2 0 0 0 7.5 19.5h1v2.75a1.25 1.25 0 0 0 2.5 0V19.5h2v2.75a1.25 1.25 0 0 0 2.5 0V19.5h1a2 2 0 0 0 2-2V11z"/></svg>
);

const AppleIcon = ({ className }: { className?: string }) => (
  <svg viewBox="0 0 24 24" className={className} fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
);

interface InstallStepsProps {
  currentPlatform?: 'android' | 'ios';
  onPlatformChange?: (platform: 'android' | 'ios') => void;
}

export const InstallSteps: React.FC<InstallStepsProps> = ({
  currentPlatform,
  onPlatformChange
}) => {
  const [internalTab, setInternalTab] = useState<'android' | 'ios'>('android');
  const activeTab = currentPlatform ?? internalTab;

  const setActiveTab = (tab: 'android' | 'ios') => {
    setInternalTab(tab);
    if (onPlatformChange) onPlatformChange(tab);
  };

  const [copiedAdb, setCopiedAdb] = useState(false);
  const [copiedIpa, setCopiedIpa] = useState(false);
  const [downloadToast, setDownloadToast] = useState<string | null>(null);

  const [versionData, setVersionData] = useState<{
    versionName: string;
    versionCode: number;
    fileSize: string;
    ipaFileSize: string;
  } | null>(null);

  React.useEffect(() => {
    fetch('/version.json')
      .then(res => res.json())
      .then(data => setVersionData(data))
      .catch(() => {});
  }, []);

  const adbCommand = 'adb install -r sked-android.apk';
  const ipaDownloadUrl = typeof window !== 'undefined' 
    ? `${window.location.origin}/downloads/sked-ios.ipa` 
    : '/downloads/sked-ios.ipa';

  const copyAdb = () => {
    navigator.clipboard.writeText(adbCommand);
    setCopiedAdb(true);
    setTimeout(() => setCopiedAdb(false), 2000);
  };

  const copyIpaLink = () => {
    navigator.clipboard.writeText(ipaDownloadUrl);
    setCopiedIpa(true);
    setTimeout(() => setCopiedIpa(false), 2000);
  };

  const triggerDownload = (tab: 'android' | 'ios') => {
    const link = document.createElement('a');
    if (tab === 'android') {
      link.href = '/downloads/sked-android.apk';
      link.download = 'sked-android.apk';
      setDownloadToast('Starting download: sked-android.apk');
    } else {
      link.href = '/downloads/sked-ios.ipa';
      link.download = 'sked-ios.ipa';
      setDownloadToast('Starting download: sked-ios.ipa');
    }
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    setTimeout(() => {
      setDownloadToast(null);
    }, 3500);
  };

  const handleTabClick = (tab: 'android' | 'ios') => {
    if (activeTab === tab) {
      // If user tapped the tab that is already active (very common on phones when they think it's the download button),
      // directly trigger the download for them!
      triggerDownload(tab);
    } else {
      setActiveTab(tab);
    }
  };

  return (
    <section id="install" className="py-24 px-6 relative max-w-7xl mx-auto">
      {/* Toast Notification when download is triggered */}
      {downloadToast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-[#141414] border border-emerald-500/40 text-emerald-400 px-5 py-3 rounded-xl shadow-2xl flex items-center gap-3 text-sm font-mono backdrop-blur-md">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{downloadToast}</span>
        </div>
      )}

      <div className="text-center mb-12 space-y-4">
        <Badge variant="secondary" className="bg-[#FF6B1A]/10 text-[#FF6B1A] border-[#FF6B1A]/30 font-mono text-xs">
          INSTALLATION PORTAL
        </Badge>
        <h2 className="text-4xl sm:text-5xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          GET SKED ON YOUR DEVICE<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-lg max-w-2xl mx-auto">
          Choose your operating system below for step-by-step installation instructions.
          Completely free, open source, and 100% on-device.
        </p>

        {/* Platform Switcher Tabs */}
        <div className="flex flex-col items-center pt-6">
          <span className="text-[11px] font-mono tracking-widest uppercase text-[#7A7774] mb-2.5">
            Step 1: Choose Your Platform
          </span>
          <div className="inline-flex p-1.5 rounded-2xl bg-[#141414] border border-[#252525] shadow-inner">
            <button
              onClick={() => handleTabClick('android')}
              className={`flex items-center gap-2.5 px-6 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 ${
                activeTab === 'android'
                  ? 'bg-[#FF6B1A] text-[#0A0A0A] shadow-md font-bold'
                  : 'text-[#7A7774] hover:text-[#E8E6E3]'
              }`}
              title="Click to select or download Android APK"
            >
              <AndroidIcon className="w-4 h-4" />
              <span>Android (APK)</span>
              {activeTab === 'android' && (
                <span className="text-[10px] bg-[#0A0A0A]/20 px-1.5 py-0.5 rounded font-mono uppercase tracking-wider font-bold">
                  Active
                </span>
              )}
            </button>
            <button
              onClick={() => handleTabClick('ios')}
              className={`flex items-center gap-2.5 px-6 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 ${
                activeTab === 'ios'
                  ? 'bg-[#38BDF8] text-[#0A0A0A] shadow-md font-bold'
                  : 'text-[#7A7774] hover:text-[#E8E6E3]'
              }`}
              title="Click to select or download iOS IPA"
            >
              <AppleIcon className="w-4 h-4" />
              <span>iOS (iPhone / iPad)</span>
              {activeTab === 'ios' && (
                <span className="text-[10px] bg-[#0A0A0A]/20 px-1.5 py-0.5 rounded font-mono uppercase tracking-wider font-bold">
                  Active
                </span>
              )}
            </button>
          </div>

          {/* Dedicated Instant Mobile Download Button (Visible only on mobile screens) */}
          <div className="mt-5 w-full max-w-sm mx-auto sm:hidden">
            <button
              onClick={() => triggerDownload(activeTab)}
              className={`w-full py-4 px-6 rounded-xl font-['Barlow_Condensed'] font-bold text-lg tracking-wide shadow-2xl flex items-center justify-center gap-2.5 transition-all active:scale-95 ${
                activeTab === 'android'
                  ? 'bg-[#FF6B1A] text-[#0A0A0A] hover:bg-[#FF6B1A]/90'
                  : 'bg-[#38BDF8] text-[#0A0A0A] hover:bg-[#38BDF8]/90'
              }`}
            >
              <Download className="w-5 h-5 animate-bounce" />
              <span>
                {activeTab === 'android' ? 'DOWNLOAD ANDROID APK' : 'DOWNLOAD iOS .IPA'}
              </span>
            </button>
            <p className="text-[11px] font-mono text-[#7A7774] text-center mt-2">
              {activeTab === 'android'
                ? `Direct APK • ${versionData?.fileSize || '12.8 MB'} • v${versionData?.versionName || '1.2.3'}`
                : `Direct IPA • ${versionData?.ipaFileSize || '7.6 MB'} • v${versionData?.versionName || '1.2.3'}`}
            </p>
          </div>
        </div>
      </div>

      {activeTab === 'android' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* Main Download Card */}
          <div className="lg:col-span-5">
            <Card className="bg-[#141414] border-[#252525] p-8 rounded-2xl relative overflow-hidden shadow-2xl">
              <div className="absolute top-0 right-0 w-32 h-32 bg-[#FF6B1A]/10 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none" />
              
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-xl bg-[#FF6B1A]/15 border border-[#FF6B1A]/30 flex items-center justify-center text-[#FF6B1A]">
                    <AndroidIcon className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="text-xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3]">
                      SKED FOR ANDROID
                    </h3>
                    <p className="text-xs font-mono text-[#7A7774]">
                      v{versionData?.versionName || '1.2.3'} • Jetpack Compose + Glance
                    </p>
                  </div>
                </div>
                <Badge className="bg-emerald-500/10 text-emerald-400 border-emerald-500/30 text-xs">
                  OTA UPDATES
                </Badge>
              </div>

              {/* Prominent Direct Download Button placed directly at TOP of card */}
              <div className="mb-6">
                <a
                  href="/downloads/sked-android.apk"
                  download="sked-android.apk"
                  className="w-full block"
                >
                  <Button className="w-full bg-[#FF6B1A] text-[#0A0A0A] hover:bg-[#FF6B1A]/90 font-bold font-['Barlow_Condensed'] text-xl py-6 tracking-wide shadow-xl flex items-center justify-center gap-2 rounded-xl">
                    <Download className="w-5 h-5" />
                    DOWNLOAD APK (DIRECT)
                  </Button>
                </a>
                <div className="flex items-center justify-between mt-2.5 px-1 text-xs text-[#7A7774] font-mono">
                  <span>File: sked-android.apk</span>
                  <span>{versionData?.fileSize || '19.4 MB'}</span>
                </div>
              </div>

              <div className="space-y-4 mb-6 text-sm text-[#7A7774]">
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Package Name</span>
                  <span className="font-mono text-[#E8E6E3]">com.sked.sked_app</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>In-App Updates</span>
                  <span className="font-mono text-emerald-400">Automatic (1-Tap OTA)</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Android Version</span>
                  <span className="font-mono text-[#E8E6E3]">Android 8.0+ (API 26+)</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Widget Support</span>
                  <span className="font-mono text-emerald-400">Glance (4x2, 4x3, 4x4, 4x5)</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Background Sync</span>
                  <span className="font-mono text-[#E8E6E3]">Periodic (WorkManager)</span>
                </div>
              </div>

              {/* Security guarantee */}
              <div className="flex items-center justify-center gap-2 mt-4 text-xs text-[#7A7774]">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>100% On-Device Auth • No Telemetry • No Proxies</span>
              </div>
            </Card>

            {/* ADB Command Box for developers */}
            <Card className="bg-[#0D0D0D] border-[#252525] p-5 rounded-xl mt-6">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-mono text-[#7A7774] flex items-center gap-2">
                  <Terminal className="w-3.5 h-3.5 text-[#FF6B1A]" />
                  PRO TIP: INSTALL VIA ADB
                </span>
                <button
                  onClick={copyAdb}
                  className="text-xs font-mono text-[#7A7774] hover:text-[#E8E6E3] flex items-center gap-1.5 transition-colors"
                >
                  {copiedAdb ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  {copiedAdb ? 'COPIED' : 'COPY'}
                </button>
              </div>
              <code className="block bg-[#0A0A0A] p-3 rounded text-xs font-mono text-emerald-400 border border-[#1A1A1A] select-all">
                {adbCommand}
              </code>
            </Card>
          </div>

          {/* Step-by-Step Instructions */}
          <div className="lg:col-span-7 space-y-4">
            <h4 className="text-sm font-mono uppercase tracking-wider text-[#7A7774] mb-4">
              Android Installation Walkthrough
            </h4>

            {[
              {
                step: '01',
                title: 'Download the APK',
                desc: 'Tap the Download APK button above to save sked-android.apk to your phone downloads folder.',
                icon: Download,
              },
              {
                step: '02',
                title: 'Allow Unknown Sources',
                desc: 'Open your phone Downloads and tap the APK file. If Android prompts "For your security, your phone is not allowed to install unknown apps", tap Settings and toggle Allow from this source.',
                icon: ShieldCheck,
              },
              {
                step: '03',
                title: 'Sign in with UMS Credentials',
                desc: 'Launch Sked and input your Registration Number & UMS Password. If Cloudflare Turnstile verification appears, tap the verification box once to complete on-device authentication.',
                icon: CheckCircle2,
              },
              {
                step: '04',
                title: 'Add the Home Screen Widget',
                desc: 'Long press anywhere on your home screen, tap Widgets, find Sked, and place the Timetable Widget. Resize to 4x2, 4x3, 4x4, or 4x5. Enjoy real-time classes and Sunday relaxation mode!',
                icon: Sparkles,
              },
            ].map((item, idx) => (
              <Card key={idx} className="bg-[#141414] border-[#252525] p-5 rounded-xl hover:border-[#FF6B1A]/40 transition-colors">
                <div className="flex items-start gap-4">
                  <div className="w-10 h-10 rounded-lg bg-[#FF6B1A]/10 border border-[#FF6B1A]/30 flex items-center justify-center font-mono font-bold text-[#FF6B1A] shrink-0 text-sm">
                    {item.step}
                  </div>
                  <div className="space-y-1">
                    <h5 className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-lg tracking-wide">
                      {item.title}
                    </h5>
                    <p className="text-sm text-[#7A7774] leading-relaxed">
                      {item.desc}
                    </p>
                  </div>
                </div>
              </Card>
            ))}

            {/* Battery Saver Tip */}
            <div className="p-4 rounded-xl bg-[#FF6B1A]/10 border border-[#FF6B1A]/20 flex items-start gap-3 text-xs text-[#E8E6E3]">
              <AlertCircle className="w-5 h-5 text-[#FF6B1A] shrink-0 mt-0.5" />
              <div>
                <span className="font-bold text-[#FF6B1A]">Background Widget Sync: </span>
                To guarantee the widget updates seamlessly in the background, set Battery Usage for Sked to <strong className="text-white">Unrestricted</strong> in Android App Info.
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          {/* iOS Card */}
          <div className="lg:col-span-5">
            <Card className="bg-[#141414] border-[#252525] p-8 rounded-2xl relative overflow-hidden shadow-2xl">
              <div className="absolute top-0 right-0 w-32 h-32 bg-sky-500/10 rounded-full blur-2xl -mr-10 -mt-10 pointer-events-none" />

              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-xl bg-sky-500/15 border border-sky-500/30 flex items-center justify-center text-sky-400">
                    <AppleIcon className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="text-xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3]">
                      SKED FOR iOS
                    </h3>
                    <p className="text-xs font-mono text-[#7A7774]">
                      v{versionData?.versionName || '1.2.3'} • Flutter Client (sked-app)
                    </p>
                  </div>
                </div>
                <Badge className="bg-sky-500/10 text-sky-400 border-sky-500/30 text-xs">
                  READY TO SIDELOAD
                </Badge>
              </div>

              {/* Primary Action: Direct IPA Download placed at TOP */}
              <div className="mb-6">
                <a
                  href="/downloads/sked-ios.ipa"
                  download="sked-ios.ipa"
                  className="w-full block"
                >
                  <Button className="w-full bg-[#38BDF8] text-[#0A0A0A] hover:bg-[#38BDF8]/90 font-bold font-['Barlow_Condensed'] text-xl py-6 tracking-wide shadow-xl flex items-center justify-center gap-2 rounded-xl">
                    <Download className="w-5 h-5" />
                    DOWNLOAD .IPA (DIRECT)
                  </Button>
                </a>
                <div className="flex items-center justify-between mt-2.5 px-1 text-xs text-[#7A7774] font-mono">
                  <span>File: sked-ios.ipa</span>
                  <span>{versionData?.ipaFileSize || '7.6 MB'}</span>
                </div>

                {/* Secondary Action: Xcode Source Zip */}
                <a
                  href="/downloads/sked-ios-source.zip"
                  download="sked-ios-source.zip"
                  className="w-full block mt-3"
                >
                  <Button variant="outline" className="w-full border-[#252525] bg-[#0F0F0F] text-[#E8E6E3] hover:bg-[#1C1C1C] hover:border-sky-500/40 text-xs font-mono py-2.5 h-auto flex items-center justify-center gap-2 transition-all">
                    <Download className="w-3.5 h-3.5 text-sky-400" />
                    DOWNLOAD XCODE SOURCE (.ZIP)
                  </Button>
                </a>
              </div>

              <div className="space-y-4 mb-6 text-sm text-[#7A7774]">
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Bundle Identifier</span>
                  <span className="font-mono text-[#E8E6E3]">com.sked.sked_app</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Framework</span>
                  <span className="font-mono text-[#E8E6E3]">Flutter / Dart (sked-app)</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Supported iOS</span>
                  <span className="font-mono text-[#E8E6E3]">iOS 14.0+ (iPhone & iPad)</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Sideload Tools</span>
                  <span className="font-mono text-emerald-400">AltStore, SideStore, Sideloadly</span>
                </div>
                <div className="flex items-center justify-between py-2 border-b border-[#252525]">
                  <span>Free Apple ID</span>
                  <span className="font-mono text-[#E8E6E3]">Fully Supported (7-day free cert)</span>
                </div>
              </div>

              <div className="flex items-center justify-center gap-2 mt-4 text-xs text-[#7A7774]">
                <ShieldCheck className="w-4 h-4 text-sky-400" />
                <span>Zero Jailbreak Required • Self-Signed</span>
              </div>
            </Card>

            {/* Sideload URL Box (Direct IPA link for SideStore/AltStore) */}
            <Card className="bg-[#0D0D0D] border-[#252525] p-5 rounded-xl mt-6">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-mono text-[#7A7774] flex items-center gap-2">
                  <AppleIcon className="w-3.5 h-3.5 text-sky-400" />
                  SIDELOAD LINK (FOR ALTSTORE / SIDESTORE)
                </span>
                <button
                  onClick={copyIpaLink}
                  className="text-xs font-mono text-[#7A7774] hover:text-[#E8E6E3] flex items-center gap-1.5 transition-colors"
                >
                  {copiedIpa ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  {copiedIpa ? 'COPIED' : 'COPY LINK'}
                </button>
              </div>
              <code className="block bg-[#0A0A0A] p-3 rounded text-xs font-mono text-sky-400 border border-[#1A1A1A] select-all truncate">
                {ipaDownloadUrl}
              </code>
              <p className="text-[11px] text-[#7A7774] mt-2 font-mono">
                Tip: Copy this link directly into SideStore or AltStore on iPhone to install without connecting to PC!
              </p>
            </Card>

            {/* Quick Web Companion Note */}
            <Card className="bg-[#0D0D0D] border-[#252525] p-5 rounded-xl mt-4">
              <span className="text-xs font-mono text-[#FF6B1A] flex items-center gap-2 mb-2 font-bold">
                <Sparkles className="w-4 h-4" />
                FASTEST OPTION: SAFARI PWA
              </span>
              <p className="text-xs text-[#7A7774] leading-relaxed">
                Open this portal in Safari on your iPhone, tap <strong className="text-[#E8E6E3]">Share</strong>, and select <strong className="text-[#E8E6E3]">Add to Home Screen</strong> for an instant app-like launcher with offline timetable storage.
              </p>
            </Card>
          </div>

          {/* iOS Steps */}
          <div className="lg:col-span-7 space-y-4">
            <h4 className="text-sm font-mono uppercase tracking-wider text-[#7A7774] mb-4">
              iOS Sideloading Walkthrough
            </h4>

            {[
              {
                step: '01',
                title: 'Download the Sked .IPA File',
                desc: 'Tap the "DOWNLOAD .IPA (DIRECT)" button on the left to save sked-ios.ipa directly on your iPhone or computer, or copy the direct sideload link.',
                icon: Download,
              },
              {
                step: '02',
                title: 'Sideload via AltStore / SideStore',
                desc: 'In AltStore or SideStore on your iPhone, tap "+" in My Apps, select the downloaded sked-ios.ipa, and sign in with your free personal Apple ID to install without jailbreak.',
                icon: CheckCircle2,
              },
              {
                step: '03',
                title: 'Or Sideload via Sideloadly / Xcode (PC or Mac)',
                desc: 'On Windows or Mac, open Sideloadly, drag sked-ios.ipa, enter your Apple ID, and click Start. Mac developers can also extract the Xcode .ZIP and run directly from Xcode (⌘R).',
                icon: Terminal,
              },
              {
                step: '04',
                title: 'Trust Developer Profile in Settings',
                desc: 'On your iPhone, navigate to Settings > General > VPN & Device Management. Tap your Apple ID profile and choose Trust "com.sked.sked_app". (On iOS 16+, enable Developer Mode in Privacy & Security).',
                icon: ShieldCheck,
              },
            ].map((item, idx) => (
              <Card key={idx} className="bg-[#141414] border-[#252525] p-5 rounded-xl hover:border-sky-500/40 transition-colors">
                <div className="flex items-start gap-4">
                  <div className="w-10 h-10 rounded-lg bg-sky-500/10 border border-sky-500/30 flex items-center justify-center font-mono font-bold text-sky-400 shrink-0 text-sm">
                    {item.step}
                  </div>
                  <div className="space-y-1">
                    <h5 className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-lg tracking-wide">
                      {item.title}
                    </h5>
                    <p className="text-sm text-[#7A7774] leading-relaxed">
                      {item.desc}
                    </p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}
    </section>
  );
};
