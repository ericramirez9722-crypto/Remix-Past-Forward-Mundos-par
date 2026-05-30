import React, { useState } from 'react';
import { motion, useMotionValue, useTransform, PanInfo } from 'framer-motion';
import { Trash2 } from 'lucide-react';

interface SwipeToRejectProps {
    children: React.ReactNode;
    onReject: () => void;
    label: string;
    disabled?: boolean;
}

export const SwipeToReject: React.FC<SwipeToRejectProps> = ({ children, onReject, label, disabled = false }) => {
    const x = useMotionValue(0);
    const [isPastThreshold, setIsPastThreshold] = useState(false);
    const THRESHOLD = 130;

    // Map the horizontal position to opacity and scales for background elements
    const opacity = useTransform(x, [-THRESHOLD, 0, THRESHOLD], [0.95, 0, 0.95]);
    const iconScale = useTransform(x, [-THRESHOLD, -80, 0, 80, THRESHOLD], [1.25, 1, 0.7, 1, 1.25]);
    
    // Dynamically choose side to render icons and color states
    const backgroundClass = useTransform(x, (value) => {
        if (disabled) return 'bg-transparent';
        if (Math.abs(value) > THRESHOLD) {
            return 'bg-red-600 border-red-500';
        }
        return 'bg-red-950/80 border-red-900/40';
    });

    const handleDrag = (event: any, info: PanInfo) => {
        if (disabled) return;
        const offset = Math.abs(info.offset.x);
        if (offset > THRESHOLD && !isPastThreshold) {
            setIsPastThreshold(true);
            if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
                try {
                    navigator.vibrate(30); // light haptic bump when threshold is crossed
                } catch {
                    // Fail-safe
                }
            }
        } else if (offset <= THRESHOLD && isPastThreshold) {
            setIsPastThreshold(false);
        }
    };

    const handleDragEnd = (event: any, info: PanInfo) => {
        if (disabled) return;
        const offset = info.offset.x;
        if (Math.abs(offset) > THRESHOLD) {
            // Trigger the reject action
            onReject();
        }
        setIsPastThreshold(false);
    };

    return (
        <motion.div 
            className="relative w-full max-w-sm overflow-hidden rounded-2xl border border-white/5 bg-neutral-950/20"
            exit={{ 
                opacity: 0, 
                height: 0, 
                scale: 0.8,
                transition: { duration: 0.35, ease: 'easeInOut' }
            }}
            layout
        >
            {/* Gesture Background Indicators */}
            {!disabled && (
                <motion.div 
                    style={{ opacity, backgroundColor: 'transparent' }}
                    className="absolute inset-0 z-0 flex items-center justify-between px-6 rounded-2xl pointer-events-none border"
                >
                    {/* Dynamically Styled Background Container */}
                    <motion.div 
                        style={{ background: backgroundClass }}
                        className="absolute inset-0 -z-10 transition-colors duration-150"
                    />

                    <motion.div 
                        style={{ scale: iconScale }}
                        className="flex items-center gap-2 text-white"
                    >
                        <Trash2 className="h-5 w-5" />
                        <span className="text-[10px] font-mono tracking-widest font-extrabold uppercase">Reject</span>
                    </motion.div>

                    <motion.div 
                        style={{ scale: iconScale }}
                        className="flex items-center gap-2 text-white text-right"
                    >
                        <span className="text-[10px] font-mono tracking-widest font-extrabold uppercase">Reject</span>
                        <Trash2 className="h-5 w-5" />
                    </motion.div>
                </motion.div>
            )}

            {/* Draggable Card Layer */}
            <motion.div
                drag={disabled ? false : "x"}
                dragDirectionLock
                dragConstraints={{ left: 0, right: 0 }}
                dragElastic={{ left: 0.6, right: 0.6 }}
                onDrag={handleDrag}
                onDragEnd={handleDragEnd}
                style={{ x }}
                className="relative z-10 cursor-grab active:cursor-grabbing touch-pan-x"
            >
                {/* Background shadow of card when swiping to add realistic depth */}
                <motion.div
                    className="absolute inset-0 bg-black/40 rounded-md -z-10 blur-sm mix-blend-multiply"
                    style={{
                        opacity: useTransform(x, [-100, 0, 100], [0.3, 0, 0.3])
                    }}
                />
                {children}
            </motion.div>
        </motion.div>
    );
};
