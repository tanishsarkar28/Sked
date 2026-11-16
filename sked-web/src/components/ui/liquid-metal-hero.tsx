"use client";

import { LiquidMetal, liquidMetalPresets } from '@paper-design/shaders-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { motion } from 'framer-motion';

interface LiquidMetalHeroProps {
  badge?: string;
  title: string;
  subtitle: string;
  primaryCtaLabel: React.ReactNode;
  secondaryCtaLabel?: React.ReactNode;
  onPrimaryCtaClick: () => void;
  onSecondaryCtaClick?: () => void;
  features?: string[];
}

export default function LiquidMetalHero({
  badge,
  title,
  subtitle,
  primaryCtaLabel,
  secondaryCtaLabel,
  onPrimaryCtaClick,
  onSecondaryCtaClick,
  features = [],
}: LiquidMetalHeroProps) {
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        delayChildren: 0.2,
        staggerChildren: 0.15
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 30 },
    visible: { 
      opacity: 1, 
      y: 0
    }
  };

  const buttonVariants = {
    hidden: { opacity: 0, scale: 0.9 },
    visible: { 
      opacity: 1, 
      scale: 1
    }
  };

  return (
    <section className="relative min-h-[calc(100vh-4rem)] flex flex-col justify-center pt-8 pb-16 sm:py-20 overflow-hidden">
      <LiquidMetal
        {...liquidMetalPresets[2]}
        style={{ position: "fixed", inset: 0, zIndex: -10 }}
      />
      
      <div className="container mx-auto px-6 lg:px-8 max-w-7xl my-auto">
        <motion.div 
          className="text-center space-y-6 sm:space-y-10"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.8, ease: [0.25, 0.1, 0.25, 1] }}
        >
          {badge && (
            <motion.div 
              className="flex justify-center"
              variants={itemVariants}
            >
              <Badge 
                variant="secondary" 
                className="bg-white/[0.06] text-foreground border border-white/15 backdrop-blur-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.25),0_4px_16px_rgba(0,0,0,0.3)] text-xs sm:text-sm py-1.5 px-4 tracking-wide rounded-full font-mono"
              >
                {badge}
              </Badge>
            </motion.div>
          )}
          
          <motion.div 
            className="space-y-6 sm:space-y-8"
            variants={itemVariants}
          >
            <motion.h1 
              role="heading" 
              aria-level={1}
              className="text-4xl sm:text-6xl lg:text-7xl xl:text-8xl font-bold text-foreground leading-tight tracking-tight"
              variants={itemVariants}
            >
              {title}
            </motion.h1>
            
            <motion.p 
              className="max-w-3xl mx-auto text-lg sm:text-xl text-foreground/70 leading-relaxed"
              variants={itemVariants}
            >
              {subtitle}
            </motion.p>
          </motion.div>
          
          <motion.div 
            className="flex flex-col sm:flex-row gap-5 justify-center items-center pt-2"
            variants={buttonVariants}
          >
            <motion.div
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              <Button 
                onClick={onPrimaryCtaClick}
                size="lg"
                className="bg-gradient-to-r from-[#FF6B1A] to-[#FF8533] text-[#0A0A0A] font-['Barlow_Condensed'] font-bold tracking-wide hover:from-[#FF7528] hover:to-[#FF8F42] transition-all duration-300 shadow-[inset_0_1px_1px_rgba(255,255,255,0.5),0_10px_32px_rgba(255,107,26,0.4)] text-lg px-8 py-6 rounded-2xl border border-[#FF8533]/50"
              >
                {primaryCtaLabel}
              </Button>
            </motion.div>
            
            {secondaryCtaLabel && onSecondaryCtaClick && (
              <motion.div
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
              >
                <Button 
                  onClick={onSecondaryCtaClick}
                  variant="outline"
                  size="lg"
                  className="bg-white/[0.06] hover:bg-white/[0.12] text-[#E8E6E3] hover:text-white font-['Barlow_Condensed'] font-bold tracking-wide border border-white/20 hover:border-sky-400/50 transition-all duration-300 backdrop-blur-xl shadow-[inset_0_1px_1px_rgba(255,255,255,0.2),0_8px_24px_rgba(0,0,0,0.4)] text-lg px-8 py-6 rounded-2xl"
                >
                  {secondaryCtaLabel}
                </Button>
              </motion.div>
            )}
          </motion.div>
          
          {features.length > 0 && (
            <motion.div 
              className="pt-8 sm:pt-16"
              variants={itemVariants}
            >
              <motion.div
                whileHover={{ y: -4 }}
                transition={{ duration: 0.3 }}
              >
                <Card className="liquid-glass-card rounded-2xl p-0 overflow-hidden">
                  <div className="py-6 px-4 sm:px-8">
                    <div className="grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-white/[0.08]">
                      {features.map((feature, index) => (
                        <motion.div 
                          key={index}
                          className="flex items-center justify-center text-center gap-2.5 py-3 md:py-0 px-4"
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ 
                            duration: 0.6, 
                            delay: 0.8 + (index * 0.1)
                          }}
                        >
                          <span className="w-2 h-2 rounded-full bg-[#FF6B1A] shadow-[0_0_8px_#FF6B1A] shrink-0" />
                          <p className="text-[#E8E6E3] font-medium text-sm sm:text-base font-['Barlow_Condensed'] tracking-wide uppercase">
                            {feature}
                          </p>
                        </motion.div>
                      ))}
                    </div>
                  </div>
                </Card>
              </motion.div>
            </motion.div>
          )}
        </motion.div>
      </div>
    </section>
  );
}
