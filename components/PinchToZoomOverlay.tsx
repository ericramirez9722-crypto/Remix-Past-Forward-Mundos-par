import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ZoomIn, ZoomOut, RefreshCcw } from 'lucide-react';
import { cn } from '../lib/utils';

interface PinchToZoomOverlayProps {
    isOpen: boolean;
    onClose: () => void;
    imageUrl: string;
    caption: string;
}

export const PinchToZoomOverlay: React.FC<PinchToZoomOverlayProps> = ({
    isOpen,
    onClose,
    imageUrl,
    caption
}) => {
    const [scale, setScale] = useState<number>(1);
    const [position, setPosition] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState<boolean>(false);
    
    const containerRef = useRef<HTMLDivElement>(null);
    const imageRef = useRef<HTMLImageElement>(null);
    
    // Multi-touch tracking refs
    const startDistance = useRef<number>(0);
    const startScale = useRef<number>(1);
    const startPan = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
    const lastTouch = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
    const lastTapTime = useRef<number>(0);

    // Reset adjustments whenever modal state changes
    useEffect(() => {
        if (isOpen) {
            setScale(1);
            setPosition({ x: 0, y: 0 });
            // Prevent body scrolling behind overlay
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }
        return () => {
            document.body.style.overflow = '';
        };
    }, [isOpen]);

    // Handle touch actions
    const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
        if (e.touches.length === 2) {
            // Pinch-to-zoom initialization
            const t1 = e.touches[0];
            const t2 = e.touches[1];
            const dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
            startDistance.current = dist;
            startScale.current = scale;
        } else if (e.touches.length === 1) {
            // Pan initialization
            setIsDragging(true);
            const t = e.touches[0];
            startPan.current = { x: t.clientX - position.x, y: t.clientY - position.y };
            lastTouch.current = { x: t.clientX, y: t.clientY };
            
            // Double-tap zoom detector
            const now = Date.now();
            const DOUBLE_TAP_DELAY = 300;
            if (now - lastTapTime.current < DOUBLE_TAP_DELAY) {
                // Toggle zoom
                if (scale > 1.5) {
                    setScale(1);
                    setPosition({ x: 0, y: 0 });
                } else {
                    // Zoom into where the tap happened
                    const rect = e.currentTarget.getBoundingClientRect();
                    const tapX = t.clientX - rect.left - rect.width / 2;
                    const tapY = t.clientY - rect.top - rect.height / 2;
                    setScale(2.5);
                    setPosition({ x: -tapX * 1.5, y: -tapY * 1.5 });
                }
                lastTapTime.current = 0; // reset
            } else {
                lastTapTime.current = now;
            }
        }
    };

    const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
        if (e.touches.length === 2 && startDistance.current > 0) {
            // Zoom gesture running
            const t1 = e.touches[0];
            const t2 = e.touches[1];
            const dist = Math.hypot(t1.clientX - t2.clientX, t1.clientY - t2.clientY);
            const factor = dist / startDistance.current;
            const targetScale = Math.min(4, Math.max(1, startScale.current * factor));
            setScale(targetScale);
        } else if (e.touches.length === 1 && isDragging && scale > 1) {
            // Panning gesture running (only allow off-center panning if zoomed)
            const t = e.touches[0];
            const deltaX = t.clientX - startPan.current.x;
            const deltaY = t.clientY - startPan.current.y;
            
            // Constrain constraints to keep portrait visible
            const maxOffset = (scale - 1) * 150; 
            setPosition({
                x: Math.min(maxOffset, Math.max(-maxOffset, deltaX)),
                y: Math.min(maxOffset * 1.3, Math.max(-maxOffset * 1.3, deltaY))
            });
            lastTouch.current = { x: t.clientX, y: t.clientY };
        }
    };

    const handleTouchEnd = () => {
        setIsDragging(false);
        startDistance.current = 0;
    };

    // Desktop mouse wheel zoom helper
    const handleWheel = (e: React.WheelEvent<HTMLDivElement>) => {
        e.preventDefault();
        const delta = e.deltaY * -0.01;
        const targetScale = Math.min(4, Math.max(1, scale + delta));
        setScale(targetScale);
        if (targetScale === 1) {
            setPosition({ x: 0, y: 0 });
        }
    };

    // Incremental button triggers
    const handleZoomIn = () => {
        setScale(prev => Math.min(4, prev + 0.5));
    };

    const handleZoomOut = () => {
        setScale(prev => {
            const next = Math.max(1, prev - 0.5);
            if (next === 1) setPosition({ x: 0, y: 0 });
            return next;
        });
    };

    const handleReset = () => {
        setScale(1);
        setPosition({ x: 0, y: 0 });
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div 
                    ref={containerRef}
                    className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-black/95 select-none touch-none overflow-hidden"
                    onWheel={handleWheel}
                    id="zoom-overlay"
                >
                    {/* Header bar controls */}
                    <motion.div 
                        initial={{ opacity: 0, y: -20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -20 }}
                        className="absolute top-0 inset-x-0 bg-neutral-950/70 backdrop-blur-md border-b border-white/5 px-4 h-16 flex items-center justify-between z-10"
                    >
                        <div className="flex flex-col">
                            <span className="text-[10px] font-mono text-yellow-400 font-extrabold uppercase tracking-widest leading-none">POLAROID MATRIX EXPAND</span>
                            <span className="text-sm font-permanent-marker text-white mt-1.5 truncate max-w-[180px] sm:max-w-xs">{caption}</span>
                        </div>

                        {/* Control actions */}
                        <div className="flex items-center gap-2">
                            <button 
                                onClick={handleZoomOut} 
                                disabled={scale <= 1}
                                className="p-2 text-white hover:text-yellow-400 disabled:opacity-30 disabled:hover:text-white transition-colors bg-white/5 rounded-full border border-white/5"
                                title="Zoom Out"
                            >
                                <ZoomOut className="h-4 w-4" />
                            </button>

                            <button 
                                onClick={handleZoomIn} 
                                disabled={scale >= 4}
                                className="p-2 text-white hover:text-yellow-400 disabled:opacity-30 disabled:hover:text-white transition-colors bg-white/5 rounded-full border border-white/5"
                                title="Zoom In"
                            >
                                <ZoomIn className="h-4 w-4" />
                            </button>

                            <button 
                                onClick={handleReset}
                                disabled={scale === 1 && position.x === 0 && position.y === 0}
                                className="p-2 text-white hover:text-yellow-400 disabled:opacity-30 disabled:hover:text-white transition-colors bg-white/5 rounded-full border border-white/5"
                                title="Reset Visuals"
                            >
                                <RefreshCcw className="h-4 w-4" />
                            </button>

                            <div className="w-px h-6 bg-white/10 mx-1" />

                            <button 
                                onClick={onClose}
                                className="p-2.5 bg-yellow-400 hover:bg-yellow-300 text-black rounded-full shadow-lg transition-transform active:scale-95 flex items-center justify-center font-extrabold hover:rotate-90 duration-200"
                                id="close-zoom-overlay"
                                title="Close Full Screen"
                            >
                                <X className="h-4 w-4 stroke-[3]" />
                            </button>
                        </div>
                    </motion.div>

                    {/* Interactive Main viewport display */}
                    <div 
                        className="flex-1 w-full flex items-center justify-center relative touch-none cursor-zoom-in"
                        onTouchStart={handleTouchStart}
                        onTouchMove={handleTouchMove}
                        onTouchEnd={handleTouchEnd}
                    >
                        {/* Background subtle hints */}
                        <div className="absolute inset-0 pointer-events-none flex flex-col items-center justify-center opacity-30 gap-1.5 font-mono text-[10px] text-white">
                            {scale === 1 ? (
                                <p className="animate-pulse">💡 Pinch to zoom • Double tap</p>
                            ) : (
                                <p>Drag to inspect microtextures</p>
                            )}
                        </div>

                        {/* Interactive Polaroid Canvas Frame wrapper */}
                        <motion.div 
                            style={{ 
                                scale,
                                x: position.x,
                                y: position.y,
                            }}
                            transition={{ type: "spring", damping: 30, stiffness: 350, mass: 0.8 }}
                            className="bg-neutral-100 p-4 pb-14 rounded-md shadow-[0_25px_60px_-15px_rgba(0,0,0,0.8)] aspect-[3/4] w-80 max-w-[90vw] flex flex-col items-center pointer-events-auto transform"
                        >
                            <div className="w-full h-full bg-neutral-900 shadow-inner relative overflow-hidden flex-grow select-none">
                                <img 
                                    ref={imageRef}
                                    src={imageUrl} 
                                    alt={caption} 
                                    referrerPolicy="no-referrer"
                                    className="w-full h-full object-cover pointer-events-none"
                                />
                            </div>
                            
                            <span className="w-full text-center text-black font-permanent-marker text-lg mt-3 truncate px-1">
                                {caption}
                            </span>
                        </motion.div>
                    </div>

                    {/* Compact Zoom Slider Footer Indicator */}
                    <motion.div 
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: 20 }}
                        className="absolute bottom-6 inset-x-0 mx-auto w-64 bg-neutral-950/80 backdrop-blur-md rounded-full px-5 py-3 border border-white/10 flex items-center gap-3.5 shadow-xl justify-between z-10 pointer-events-auto"
                    >
                        <span className="text-[9px] font-mono text-neutral-400 font-extrabold uppercase shrink-0">Scale</span>
                        <input 
                            type="range"
                            min="1"
                            max="4"
                            step="0.05"
                            value={scale}
                            onChange={(e) => {
                                const targetScale = parseFloat(e.target.value);
                                setScale(targetScale);
                                if (targetScale === 1) setPosition({ x: 0, y: 0 });
                            }}
                            className="flex-1 accent-yellow-400 h-1 bg-neutral-800 rounded-lg appearance-none cursor-pointer"
                        />
                        <span className="text-xs font-mono text-yellow-400 font-bold shrink-0 w-10 text-right">
                            {scale.toFixed(1)}x
                        </span>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
};
