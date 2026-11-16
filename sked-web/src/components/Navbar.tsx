import React, { useState } from 'react';
import { LiquidNavbar, NimbusIcons, LiquidNavItem } from '@/components/ui/liquid-glass-navbar';

interface NavbarProps {
  onInstallClick?: (platform?: 'android' | 'ios') => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onInstallClick }) => {
  const [activeTab, setActiveTab] = useState(0);

  const navItems: LiquidNavItem[] = [
    { label: "Features", href: "#features", icon: NimbusIcons.globe, fx: "spin" },
    { label: "Install Guide", href: "#install", icon: NimbusIcons.drop, fx: "drop" },
    { label: "Widget Preview", href: "#widget", icon: NimbusIcons.home, fx: "glow" },
    { label: "Developer", href: "#developer", icon: NimbusIcons.user, fx: "wave" },
  ];

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-[#0A0A0A]/85 backdrop-blur-md border-b border-[#252525]">
      <div className="container mx-auto px-4 sm:px-6 h-20 flex items-center justify-between max-w-7xl">
        <a href="#" className="flex items-center gap-3 group">
          <div className="w-9 h-9 rounded-xl bg-[#FF6B1A]/15 border border-[#FF6B1A]/30 flex items-center justify-center font-bold text-[#FF6B1A] font-['Barlow_Condensed'] text-xl group-hover:scale-105 transition-transform">
            S
          </div>
          <div className="flex flex-col">
            <span className="font-['Barlow_Condensed'] font-bold text-2xl tracking-wider text-[#FF6B1A] leading-none">
              SKED<span className="text-[#E8E6E3]">.</span>
            </span>
            <span className="hidden sm:inline-block text-[9px] font-mono text-[#7A7774] tracking-widest uppercase mt-0.5">
              LPU COMPANION
            </span>
          </div>
        </a>

        {/* Liquid Glass Navbar */}
        <div className="flex items-center">
          <LiquidNavbar
            items={navItems}
            active={activeTab}
            onChange={(idx) => setActiveTab(idx)}
            tone="dark"
            sound={false}
          />
        </div>
      </div>
    </header>
  );
};
