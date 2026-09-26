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
                className="bg-foreground/10 text-foreground border-foreground/20 hover:bg-foreground/20 transition-colors duration-300 backdrop-blur-sm text-xs sm:text-sm py-1.5 px-3.5 tracking-wide"
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
                className="bg-foreground text-background hover:bg-foreground/90 transition-all duration-300 shadow-2xl text-lg px-8 py-6 font-semibold"
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
                  className="border-foreground/30 text-foreground hover:bg-foreground/10 hover:border-foreground/50 transition-all duration-300 backdrop-blur-sm text-lg px-8 py-6 font-semibold"
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
                <Card className="bg-[#141414]/80 border-[#252525] backdrop-blur-md shadow-2xl hover:border-[#FF6B1A]/30 transition-colors">
                  <div className="py-6 px-4 sm:px-8">
                    <div className="grid grid-cols-1 md:grid-cols-3 divide-y md:divide-y-0 md:divide-x divide-[#252525]">
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
                          <span className="w-2 h-2 rounded-full bg-[#FF6B1A] shrink-0" />
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
