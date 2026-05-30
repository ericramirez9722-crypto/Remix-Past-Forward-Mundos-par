import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '../lib/utils';
import { CheckCircle2, AlertCircle, Loader2, Hourglass, Sparkles } from 'lucide-react';

interface TimelineVisualizerProps {
    items: string[];
    generatedImages: Record<string, { status: string; url?: string; error?: string }>;
    currentItem: string | null;
    currentStage: 'preparing' | 'generating' | 'idle';
    itemProgress: number; // 0 to 100 for current active item preparation
}

const getChemicalStageMessage = (progress: number, stage: 'preparing' | 'generating' | 'idle') => {
    if (stage === 'generating') {
        return "Exposing emulsion plate with deep generative neural solver...";
    }
    if (stage === 'idle') {
        return "Darkroom sequence complete.";
    }
    if (progress < 25) {
        return "Sterilizing glass negative plate and preparing chemical developer bath...";
    }
    if (progress < 50) {
        return "Calibrating color-temperature sensors and darkroom filtration...";
    }
    if (progress < 75) {
        return "Stirring fresh silver halide chemical developer emulsion...";
    }
    return "Fixing photographic paper onto vacuum easel plate...";
};

export const TimelineVisualizer: React.FC<TimelineVisualizerProps> = ({
    items,
    generatedImages,
    currentItem,
    currentStage,
    itemProgress
}) => {
    // Calculate global metrics
    const totalItems = items.length;
    const completedItemsCount = items.filter(
        item => generatedImages[item]?.status === 'done'
    ).length;
    const errorItemsCount = items.filter(
        item => generatedImages[item]?.status === 'error'
    ).length;
    
    const activeIndex = currentItem ? items.indexOf(currentItem) : -1;
    const isCompleted = completedItemsCount + errorItemsCount === totalItems && totalItems > 0;

    // Overall progress percentage
    // Each finished item counts as 1/total. The current active item adds its preparation progress up to half of its share, 
    // and the generation phase adds the remaining share.
    let globalProgress = (completedItemsCount / totalItems) * 100;
    if (currentItem && activeIndex !== -1 && currentStage !== 'idle') {
        const itemShare = 100 / totalItems;
        const currentProgressContribution = currentStage === 'preparing' 
            ? (itemProgress / 100) * (itemShare * 0.6) 
            : (itemShare * 0.6) + ((1 - 0.6) * itemShare); // simple estimate for generating phase
        globalProgress += currentProgressContribution;
    }
    globalProgress = Math.min(100, Math.max(0, globalProgress));

    return (
        <div className="w-full max-w-4xl mx-auto mb-8 relative px-4 z-20">
            {/* Main glassmorphism development container */}
            <div className="bg-neutral-900/85 backdrop-blur-md rounded-2xl border border-yellow-500/10 p-5 md:p-6 shadow-2xl relative overflow-hidden">
                
                {/* Background ambient darkroom glow */}
                <div className="absolute top-0 right-0 w-48 h-48 bg-yellow-500/5 rounded-full blur-3xl pointer-events-none" />
                <div className="absolute -bottom-10 -left-10 w-48 h-48 bg-amber-500/5 rounded-full blur-3xl pointer-events-none" />

                {/* Top header stats */}
                <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 mb-5 border-b border-white/5 pb-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="flex h-2 w-2 relative">
                                <span className={cn(
                                    "animate-ping absolute inline-flex h-full w-full rounded-full opacity-75",
                                    isCompleted ? "bg-green-400" : "bg-yellow-400"
                                )}></span>
                                <span className={cn(
                                    "relative inline-flex rounded-full h-2 w-2",
                                    isCompleted ? "bg-green-500" : "bg-yellow-500"
                                )}></span>
                            </span>
                            <h3 className="text-xs font-mono uppercase tracking-widest text-neutral-400 font-extrabold">
                                {isCompleted ? "DARKROOM DISPATCH" : "DEVELOPMENT SEQUENCER ACTIVE"}
                            </h3>
                        </div>
                        
                        <p className="text-lg md:text-xl font-permanent-marker text-neutral-100 mt-1">
                            {currentItem ? (
                                <span>Reimagining: <span className="text-yellow-400">{currentItem}</span></span>
                            ) : isCompleted ? (
                                <span className="text-green-400">All Masterworks Compiled!</span>
                            ) : (
                                "Initializing chemical matrix..."
                            )}
                        </p>
                    </div>

                    <div className="flex flex-col sm:items-end">
                        <span className="text-xs font-mono text-neutral-400 tracking-wider">
                            Chemical Master Batch: <span className="text-neutral-100 font-bold">{completedItemsCount}/{totalItems}</span> Completed
                        </span>
                        {errorItemsCount > 0 && (
                            <span className="text-[10px] font-mono text-red-400 mt-0.5">
                                {errorItemsCount} Plate{errorItemsCount > 1 ? 's' : ''} Failed
                            </span>
                        )}
                    </div>
                </div>

                {/* Subtext current event & status bar */}
                <div className="mb-6 bg-black/40 rounded-xl p-3 border border-white/5 flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                        {currentStage === 'preparing' && (
                            <Hourglass className="h-4 w-4 text-yellow-400 animate-spin" style={{ animationDuration: '3s' }} />
                        )}
                        {currentStage === 'generating' && (
                            <Loader2 className="h-4 w-4 text-amber-500 animate-spin" />
                        )}
                        {currentStage === 'idle' && isCompleted && (
                            <Sparkles className="h-4 w-4 text-green-400 animate-pulse" />
                        )}
                        {currentStage === 'idle' && !isCompleted && (
                            <div className="h-1.5 w-1.5 rounded-full bg-neutral-600 animate-pulse" />
                        )}
                        
                        <span className="text-xs font-mono text-neutral-300 font-medium">
                            {getChemicalStageMessage(itemProgress, currentStage)}
                        </span>
                    </div>

                    {currentStage === 'preparing' && (
                        <span className="text-xs font-mono text-yellow-400 font-bold shrink-0">
                            Stirring... {Math.round(itemProgress)}%
                        </span>
                    )}
                </div>

                {/* Main Progress Bar Container */}
                <div className="relative w-full h-2.5 bg-neutral-950 rounded-full border border-white/5 p-0.5 overflow-hidden mb-8">
                    {/* Glowing progress runner */}
                    <motion.div 
                        className="h-full bg-gradient-to-r from-amber-600 via-yellow-500 to-yellow-300 rounded-full relative shadow-[0_0_8px_rgba(234,179,8,0.4)]"
                        initial={{ width: 0 }}
                        animate={{ width: `${globalProgress}%` }}
                        transition={{ duration: 0.4, ease: "easeOut" }}
                    >
                        <div className="absolute right-0 top-0 bottom-0 w-2 bg-white rounded-full animate-pulse" />
                    </motion.div>
                </div>

                {/* Timeline Node Chain */}
                <div className="grid grid-cols-2 md:grid-cols-6 gap-4 md:gap-2 relative">
                    {items.map((item, index) => {
                        const imgState = generatedImages[item];
                        const isCurrent = currentItem === item;
                        const isDone = imgState?.status === 'done';
                        const isError = imgState?.status === 'error';
                        const isQueued = !isCurrent && !isDone && !isError;

                        return (
                            <motion.div 
                                key={item} 
                                className={cn(
                                    "flex flex-col items-center p-3 rounded-xl border transition-all duration-300",
                                    isCurrent && "bg-yellow-500/5 border-yellow-500/30 shadow-[0_0_15px_rgba(234,179,8,0.05)]",
                                    isDone && "bg-neutral-800/20 border-white/5",
                                    isError && "bg-red-950/10 border-red-500/20",
                                    isQueued && "bg-transparent border-transparent opacity-40 hover:opacity-60"
                                )}
                                initial={{ opacity: 0, y: 10 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: index * 0.05 }}
                            >
                                {/* Step Circle Indicator */}
                                <div className="flex items-center justify-center w-8 h-8 rounded-full mb-2.5 relative">
                                    <AnimatePresence mode="wait">
                                        {isDone ? (
                                            <motion.div 
                                                key="done"
                                                initial={{ scale: 0 }} 
                                                animate={{ scale: 1 }} 
                                                exit={{ scale: 0 }}
                                            >
                                                <CheckCircle2 className="h-6 w-6 text-green-400 drop-shadow-[0_0_4px_rgba(74,222,128,0.4)]" />
                                            </motion.div>
                                        ) : isError ? (
                                            <motion.div 
                                                key="error"
                                                initial={{ scale: 0 }} 
                                                animate={{ scale: 1 }} 
                                                exit={{ scale: 0 }}
                                            >
                                                <AlertCircle className="h-6 w-6 text-red-500 drop-shadow-[0_0_4px_rgba(239,68,68,0.4)] animate-bounce" />
                                            </motion.div>
                                        ) : isCurrent && currentStage === 'preparing' ? (
                                            <motion.div 
                                                key="preparing"
                                                initial={{ scale: 0 }}
                                                animate={{ scale: 1 }}
                                                exit={{ scale: 0 }}
                                                className="relative flex items-center justify-center"
                                            >
                                                <div className="absolute inset-0 rounded-full border border-yellow-400 animate-ping opacity-60" />
                                                <div className="bg-yellow-400 p-1.5 rounded-full">
                                                    <Hourglass className="h-3.5 w-3.5 text-black animate-spin" style={{ animationDuration: '2s' }} />
                                                </div>
                                            </motion.div>
                                        ) : isCurrent && currentStage === 'generating' ? (
                                            <motion.div 
                                                key="generating"
                                                initial={{ scale: 0 }}
                                                animate={{ scale: 1 }}
                                                exit={{ scale: 0 }}
                                                className="relative flex items-center justify-center"
                                            >
                                                <div className="absolute inset-0 rounded-full border-2 border-amber-500 animate-pulse" />
                                                <div className="bg-amber-500 p-1.5 rounded-full">
                                                    <Loader2 className="h-3.5 w-3.5 text-black animate-spin" />
                                                </div>
                                            </motion.div>
                                        ) : (
                                            <motion.div 
                                                key="queued"
                                                initial={{ scale: 0.8 }} 
                                                animate={{ scale: 1 }}
                                                className="w-5 h-5 rounded-full border-2 border-neutral-700 bg-neutral-900 flex items-center justify-center"
                                            >
                                                <span className="text-[9px] font-mono font-bold text-neutral-500">{index + 1}</span>
                                            </motion.div>
                                        )}
                                    </AnimatePresence>
                                </div>

                                {/* Item Title */}
                                <span className={cn(
                                    "text-xs font-bold leading-tight text-center font-mono uppercase tracking-wider",
                                    isCurrent && "text-yellow-400",
                                    isDone && "text-neutral-200",
                                    isError && "text-red-400",
                                    isQueued && "text-neutral-500"
                                )}>
                                    {item}
                                </span>

                                {/* Item Status Label */}
                                <span className={cn(
                                    "text-[9px] font-mono tracking-wide mt-1 uppercase font-bold",
                                    isCurrent && (currentStage === 'preparing' ? "text-yellow-500 animate-pulse" : "text-amber-500 animate-pulse"),
                                    isDone && "text-green-500/80",
                                    isError && "text-red-500",
                                    isQueued && "text-neutral-600"
                                )}>
                                    {isCurrent ? (currentStage === 'preparing' ? "STIRRING" : "EXPOSING") :
                                     isDone ? "COMPILED" :
                                     isError ? "FAILED" : "QUEUED"}
                                </span>
                            </motion.div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
