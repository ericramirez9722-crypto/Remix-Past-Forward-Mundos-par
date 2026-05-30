/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/
import React, { useState, ChangeEvent, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { generateStyledImage } from './services/geminiService';
import PolaroidCard from './components/PolaroidCard';
import { TimelineVisualizer } from './components/TimelineVisualizer';
import { SwipeToReject } from './components/SwipeToReject';
import { ShareModal } from './components/ShareModal';
import { PinchToZoomOverlay } from './components/PinchToZoomOverlay';
import { createAlbumPage } from './lib/albumUtils';
import Footer from './components/Footer';
import { cn } from './lib/utils';
import { Undo, Share2 } from 'lucide-react';

const DECADES = ['1950s', '1960s', '1970s', '1980s', '1990s', '2000s'];
const PARALLEL_WORLDS = ['Cyberpunk', 'Fantasy', 'Noir', 'Steampunk', 'Space Opera', 'Post-Apocalyptic'];

type Mode = 'decades' | 'parallel-worlds';

// Pre-defined positions for a scattered look on desktop
const POSITIONS = [
    { top: '5%', left: '10%', rotate: -8 },
    { top: '15%', left: '60%', rotate: 5 },
    { top: '45%', left: '5%', rotate: 3 },
    { top: '2%', left: '35%', rotate: 10 },
    { top: '40%', left: '70%', rotate: -12 },
    { top: '50%', left: '38%', rotate: -3 },
];

const GHOST_POLAROIDS_CONFIG = [
  { initial: { x: "-150%", y: "-100%", rotate: -30 }, transition: { delay: 0.2 } },
  { initial: { x: "150%", y: "-80%", rotate: 25 }, transition: { delay: 0.4 } },
  { initial: { x: "-120%", y: "120%", rotate: 45 }, transition: { delay: 0.6 } },
  { initial: { x: "180%", y: "90%", rotate: -20 }, transition: { delay: 0.8 } },
  { initial: { x: "0%", y: "-200%", rotate: 0 }, transition: { delay: 0.5 } },
  { initial: { x: "100%", y: "150%", rotate: 10 }, transition: { delay: 0.3 } },
];


type ImageStatus = 'pending' | 'done' | 'error';
interface GeneratedImage {
    status: ImageStatus;
    url?: string;
    error?: string;
}

const primaryButtonClasses = "font-permanent-marker text-xl text-center text-black bg-yellow-400 py-3 px-8 rounded-sm transform transition-transform duration-200 hover:scale-105 hover:-rotate-2 hover:bg-yellow-300 shadow-[2px_2px_0px_2px_rgba(0,0,0,0.2)]";
const secondaryButtonClasses = "font-permanent-marker text-xl text-center text-white bg-white/10 backdrop-blur-sm border-2 border-white/80 py-3 px-8 rounded-sm transform transition-transform duration-200 hover:scale-105 hover:rotate-2 hover:bg-white hover:text-black";

const useMediaQuery = (query: string) => {
    const [matches, setMatches] = useState(false);
    useEffect(() => {
        const media = window.matchMedia(query);
        if (media.matches !== matches) {
            setMatches(media.matches);
        }
        const listener = () => setMatches(media.matches);
        window.addEventListener('resize', listener);
        return () => window.removeEventListener('resize', listener);
    }, [matches, query]);
    return matches;
};

function App() {
    const [uploadedImage, setUploadedImage] = useState<string | null>(null);
    const [generatedImages, setGeneratedImages] = useState<Record<string, GeneratedImage>>({});
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [isDownloading, setIsDownloading] = useState<boolean>(false);
    const [appState, setAppState] = useState<'idle' | 'image-uploaded' | 'generating' | 'results-shown'>('idle');
    const [currentMode, setCurrentMode] = useState<Mode>('decades');
    const [currentItem, setCurrentItem] = useState<string | null>(null);
    const [currentStage, setCurrentStage] = useState<'preparing' | 'generating' | 'idle'>('idle');
    const [itemProgress, setItemProgress] = useState<number>(0);
    const dragAreaRef = useRef<HTMLDivElement>(null);
    const isMobile = useMediaQuery('(max-width: 768px)');
    const [rejectedItems, setRejectedItems] = useState<string[]>([]);
    const [isShareModalOpen, setIsShareModalOpen] = useState<boolean>(false);
    const [zoomImage, setZoomImage] = useState<{ url: string; caption: string } | null>(null);

    const triggerVibration = (pattern: number | number[]) => {
        if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
            navigator.vibrate(pattern);
        }
    };

    const handleRejectItem = (item: string) => {
        setRejectedItems(prev => [...prev, item]);
        triggerVibration(80);
    };

    useEffect(() => {
        const handleSharedState = async () => {
            const hash = window.location.hash;
            if (hash && hash.startsWith('#share=')) {
                try {
                    const encodedState = hash.substring(7);
                    const parsedState = JSON.parse(decodeURIComponent(encodedState));
                    if (parsedState.original) {
                        setUploadedImage(parsedState.original);
                        if (parsedState.mode) {
                            setCurrentMode(parsedState.mode);
                        }
                        if (parsedState.rejected) {
                            setRejectedItems(parsedState.rejected);
                        }
                        setAppState('image-uploaded');
                        
                        // Clear the hash without refreshing to keep url pretty
                        window.history.replaceState(null, '', window.location.pathname);
                    }
                } catch (e) {
                    console.error("Failed to parse shared state from URL hash:", e);
                }
            }
        };
        handleSharedState();
    }, []);


    const handleImageUpload = (e: ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            const reader = new FileReader();
            reader.onloadend = () => {
                setUploadedImage(reader.result as string);
                setAppState('image-uploaded');
                setGeneratedImages({}); // Clear previous results
            };
            reader.readAsDataURL(file);
        }
    };

    const handleGenerateClick = async () => {
        if (!uploadedImage) return;

        triggerVibration([100, 50, 100]);

        setIsLoading(true);
        setAppState('generating');
        setRejectedItems([]);
        
        const items = currentMode === 'decades' ? DECADES : PARALLEL_WORLDS;
        const initialImages: Record<string, GeneratedImage> = {};
        items.forEach(item => {
            initialImages[item] = { status: 'pending' };
        });
        setGeneratedImages(initialImages);

        const concurrencyLimit = 1; // Process one item at a time to respect rate limits
        const itemsQueue = [...items];

        const processItem = async (item: string) => {
            try {
                // Set current item state and stage
                setCurrentItem(item);
                setCurrentStage('preparing');
                setItemProgress(0);

                // Add a significant fixed delay between items to respect rate limits
                // Animate progress smoothly from 0 to 100 over 6 seconds (6000ms)
                const startTime = Date.now();
                const delayDuration = 6000;
                await new Promise<void>((resolve) => {
                    const timer = setInterval(() => {
                        const elapsed = Date.now() - startTime;
                        const progress = Math.min(100, (elapsed / delayDuration) * 100);
                        setItemProgress(progress);
                        if (elapsed >= delayDuration) {
                            clearInterval(timer);
                            resolve();
                        }
                    }, 50);
                });

                // Transition to generating phase
                setCurrentStage('generating');
                setItemProgress(100);

                const prompt = `Reimagine the person in this photo in the style of the ${item}. This includes clothing, hairstyle, photo quality, and the overall aesthetic of that ${currentMode === 'decades' ? 'decade' : 'world'}. The output must be a photorealistic image showing the person clearly.`;
                const resultUrl = await generateStyledImage(uploadedImage, prompt);
                setGeneratedImages(prev => ({
                    ...prev,
                    [item]: { status: 'done', url: resultUrl },
                }));
                triggerVibration(150);
            } catch (err) {
                const errorMessage = err instanceof Error ? err.message : "An unknown error occurred.";
                setGeneratedImages(prev => ({
                    ...prev,
                    [item]: { status: 'error', error: errorMessage },
                }));
                console.error(`Failed to generate image for ${item}:`, err);
            }
        };

        const workers = Array(concurrencyLimit).fill(null).map(async () => {
            while (itemsQueue.length > 0) {
                const item = itemsQueue.shift();
                if (item) {
                     await processItem(item);
                }
            }
        });

        await Promise.all(workers);

        setIsLoading(false);
        setAppState('results-shown');
        setCurrentItem(null);
        setCurrentStage('idle');
        setItemProgress(0);
        triggerVibration([200, 100, 200]);
    };

    const handleRegenerateItem = async (item: string) => {
        if (!uploadedImage) return;

        // Prevent re-triggering if a generation is already in progress
        if (generatedImages[item]?.status === 'pending') {
            return;
        }

        triggerVibration(100);
        
        console.log(`Regenerating image for ${item}...`);

        // Set the specific item to 'pending' to show the loading spinner
        setGeneratedImages(prev => ({
            ...prev,
            [item]: { status: 'pending' },
        }));

        // Call the generation service for the specific item
        try {
            // Delay to prevent rapid manual regeneration from hitting limits
            await new Promise(resolve => setTimeout(resolve, 3000));
            
            const prompt = `Reimagine the person in this photo in the style of the ${item}. This includes clothing, hairstyle, photo quality, and the overall aesthetic of that ${currentMode === 'decades' ? 'decade' : 'world'}. The output must be a photorealistic image showing the person clearly.`;
            const resultUrl = await generateStyledImage(uploadedImage, prompt);
            setGeneratedImages(prev => ({
                ...prev,
                [item]: { status: 'done', url: resultUrl },
            }));
            triggerVibration(150);
        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : "An unknown error occurred.";
            setGeneratedImages(prev => ({
                ...prev,
                [item]: { status: 'error', error: errorMessage },
            }));
            console.error(`Failed to regenerate image for ${item}:`, err);
        }
    };
    
    const handleReset = () => {
        setUploadedImage(null);
        setGeneratedImages({});
        setAppState('idle');
        setCurrentItem(null);
        setCurrentStage('idle');
        setItemProgress(0);
        setRejectedItems([]);
    };

    const handleDownloadIndividualImage = (item: string) => {
        const image = generatedImages[item];
        if (image?.status === 'done' && image.url) {
            const link = document.createElement('a');
            link.href = image.url;
            link.download = `past-forward-${item}.jpg`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }
    };

    const handleDownloadAlbum = async () => {
        setIsDownloading(true);
        try {
            const items = currentMode === 'decades' ? DECADES : PARALLEL_WORLDS;
            const activeItems = items.filter(it => !rejectedItems.includes(it));

            if (activeItems.length === 0) {
                alert("Please make sure you have at least one active portrait to compile into your album.");
                return;
            }

            const imageData = Object.entries(generatedImages)
                .filter(([item, image]) => (image as GeneratedImage).status === 'done' && (image as GeneratedImage).url && !rejectedItems.includes(item))
                .reduce((acc, [item, image]) => {
                    acc[item] = (image as GeneratedImage).url!;
                    return acc;
                }, {} as Record<string, string>);

            if (Object.keys(imageData).length < activeItems.length) {
                alert("Please wait for all active images to finish generating before downloading the album.");
                return;
            }

            const albumDataUrl = await createAlbumPage(imageData);

            const link = document.createElement('a');
            link.href = albumDataUrl;
            link.download = 'past-forward-album.jpg';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

        } catch (error) {
            console.error("Failed to create or download album:", error);
            alert("Sorry, there was an error creating your album. Please try again.");
        } finally {
            setIsDownloading(false);
        }
    };

    return (
        <main className="bg-black text-neutral-200 min-h-screen w-full flex flex-col items-center justify-center p-4 pb-24 overflow-hidden relative">
            <div className="absolute top-0 left-0 w-full h-full bg-grid-white/[0.05]"></div>
            
            <div className="z-10 flex flex-col items-center justify-center w-full h-full flex-1 min-h-0">
                <div className="text-center mb-10">
                    <h1 className="text-6xl md:text-8xl font-caveat font-bold text-neutral-100">Past Forward</h1>
                    <p className="font-permanent-marker text-neutral-300 mt-2 text-xl tracking-wide">
                        {currentMode === 'decades' ? 'Generate yourself through the decades.' : 'Explore parallel worlds of yourself.'}
                    </p>
                    
                    {appState === 'image-uploaded' && (
                        <div className="flex justify-center mt-6">
                            <div className="bg-white/5 backdrop-blur-md p-1 rounded-full border border-white/10 flex gap-1">
                                <button 
                                    onClick={() => setCurrentMode('decades')}
                                    className={cn(
                                        "px-6 py-2 rounded-full font-permanent-marker text-sm transition-all duration-300",
                                        currentMode === 'decades' ? "bg-yellow-400 text-black shadow-lg" : "text-neutral-400 hover:text-white"
                                    )}
                                >
                                    Decades
                                </button>
                                <button 
                                    onClick={() => setCurrentMode('parallel-worlds')}
                                    className={cn(
                                        "px-6 py-2 rounded-full font-permanent-marker text-sm transition-all duration-300",
                                        currentMode === 'parallel-worlds' ? "bg-yellow-400 text-black shadow-lg" : "text-neutral-400 hover:text-white"
                                    )}
                                >
                                    Parallel Worlds
                                </button>
                            </div>
                        </div>
                    )}
                </div>

                {appState === 'idle' && (
                     <div className="relative flex flex-col items-center justify-center w-full">
                        {/* Ghost polaroids for intro animation */}
                        {GHOST_POLAROIDS_CONFIG.map((config, index) => (
                             <motion.div
                                key={index}
                                className="absolute w-80 h-[26rem] rounded-md p-4 bg-neutral-100/10 blur-sm"
                                initial={config.initial}
                                animate={{
                                    x: "0%", y: "0%", rotate: (Math.random() - 0.5) * 20,
                                    scale: 0,
                                    opacity: 0,
                                }}
                                transition={{
                                    ...config.transition,
                                    ease: "circOut",
                                    duration: 2,
                                }}
                            />
                        ))}
                        <motion.div
                             initial={{ opacity: 0, scale: 0.8 }}
                             animate={{ opacity: 1, scale: 1 }}
                             transition={{ delay: 2, duration: 0.8, type: 'spring' }}
                             className="flex flex-col items-center"
                        >
                            <label htmlFor="file-upload" className="cursor-pointer group transform hover:scale-105 transition-transform duration-300">
                                 <PolaroidCard 
                                     caption="Click to begin"
                                     status="done"
                                 />
                            </label>
                            <input id="file-upload" type="file" className="hidden" accept="image/png, image/jpeg, image/webp" onChange={handleImageUpload} />
                            <p className="mt-8 font-permanent-marker text-neutral-500 text-center max-w-xs text-lg">
                                Click the polaroid to upload your photo and start your journey through time.
                            </p>
                        </motion.div>
                    </div>
                )}

                {appState === 'image-uploaded' && uploadedImage && (
                    <div className="flex flex-col items-center gap-6">
                         <PolaroidCard 
                            imageUrl={uploadedImage} 
                            caption="Your Photo" 
                            status="done"
                         />
                         <div className="flex items-center gap-4 mt-4">
                            <button onClick={handleReset} className={secondaryButtonClasses}>
                                Different Photo
                            </button>
                            <button onClick={handleGenerateClick} className={primaryButtonClasses}>
                                Generate
                            </button>
                         </div>
                    </div>
                )}

                {(appState === 'generating' || appState === 'results-shown') && (
                     <>
                        <TimelineVisualizer
                            items={currentMode === 'decades' ? DECADES : PARALLEL_WORLDS}
                            generatedImages={generatedImages}
                            currentItem={currentItem}
                            currentStage={currentStage}
                            itemProgress={itemProgress}
                        />

                        {isMobile ? (
                            <div className="w-full max-w-sm flex-1 overflow-y-auto mt-4 space-y-6 p-4">
                                {rejectedItems.length > 0 && (
                                    <motion.div 
                                        initial={{ opacity: 0, scale: 0.95, y: -10 }}
                                        animate={{ opacity: 1, scale: 1, y: 0 }}
                                        className="bg-neutral-900/90 backdrop-blur-md rounded-xl border border-white/10 p-3.5 flex items-center justify-between shadow-lg mb-4"
                                    >
                                        <div className="flex flex-col">
                                            <span className="text-[10px] font-mono text-neutral-400 uppercase tracking-wider font-bold">Discarded Portraits</span>
                                            <span className="text-xs text-neutral-200 mt-0.5 font-sans">
                                                {rejectedItems.length} card{rejectedItems.length > 1 ? 's' : ''} removed
                                            </span>
                                        </div>
                                        <button 
                                            onClick={() => setRejectedItems([])}
                                            className="text-xs font-permanent-marker text-yellow-400 hover:text-yellow-300 flex items-center gap-1.5 transition-colors bg-white/5 py-1.5 px-3 rounded-md border border-yellow-500/10 hover:border-yellow-400/30"
                                        >
                                            <Undo className="h-3.5 w-3.5" />
                                            Restore All
                                        </button>
                                    </motion.div>
                                )}

                                <AnimatePresence mode="popLayout">
                                    {(currentMode === 'decades' ? DECADES : PARALLEL_WORLDS)
                                        .filter(item => !rejectedItems.includes(item))
                                        .map((item) => {
                                            const itemStatus = generatedImages[item]?.status || 'pending';
                                            const isPending = itemStatus === 'pending';

                                            return (
                                                <div key={item} className="flex justify-center w-full">
                                                    <SwipeToReject
                                                        onReject={() => handleRejectItem(item)}
                                                        label={item}
                                                        disabled={isPending}
                                                    >
                                                        <PolaroidCard
                                                            caption={`${item} Self-Portrait`}
                                                            status={itemStatus}
                                                            imageUrl={generatedImages[item]?.url}
                                                            error={generatedImages[item]?.error}
                                                            onShake={() => handleRegenerateItem(item)}
                                                            onDownload={() => handleDownloadIndividualImage(item)}
                                                            isMobile={isMobile}
                                                            onClick={generatedImages[item]?.url ? () => setZoomImage({
                                                                url: generatedImages[item].url!,
                                                                caption: `${item} Self-Portrait`
                                                            }) : undefined}
                                                        />
                                                    </SwipeToReject>
                                                </div>
                                            );
                                        })
                                    }
                                </AnimatePresence>
                            </div>
                        ) : (
                            <div ref={dragAreaRef} className="relative w-full max-w-5xl h-[600px] mt-4">
                                {(currentMode === 'decades' ? DECADES : PARALLEL_WORLDS).map((item, index) => {
                                    const { top, left, rotate } = POSITIONS[index];
                                    return (
                                        <motion.div
                                            key={item}
                                            className="absolute cursor-grab active:cursor-grabbing"
                                            style={{ top, left }}
                                            initial={{ opacity: 0, scale: 0.5, y: 100, rotate: 0 }}
                                            animate={{ 
                                                opacity: 1, 
                                                scale: 1, 
                                                y: 0,
                                                rotate: `${rotate}deg`,
                                            }}
                                            transition={{ type: 'spring', stiffness: 100, damping: 20, delay: index * 0.15 }}
                                        >
                                            <PolaroidCard 
                                                dragConstraintsRef={dragAreaRef}
                                                caption={`${item} Self-Portrait`}
                                                status={generatedImages[item]?.status || 'pending'}
                                                imageUrl={generatedImages[item]?.url}
                                                error={generatedImages[item]?.error}
                                                onShake={() => handleRegenerateItem(item)}
                                                onDownload={() => handleDownloadIndividualImage(item)}
                                                isMobile={isMobile}
                                                onClick={generatedImages[item]?.url ? () => setZoomImage({
                                                    url: generatedImages[item].url!,
                                                    caption: `${item} Self-Portrait`
                                                }) : undefined}
                                            />
                                        </motion.div>
                                    );
                                })}
                            </div>
                        )}
                         <div className="h-auto py-6 mt-4 flex items-center justify-center">
                            {appState === 'results-shown' && (
                                <div className="flex flex-col sm:flex-row items-center gap-4">
                                    <button 
                                        onClick={handleDownloadAlbum} 
                                        disabled={isDownloading} 
                                        className={`${primaryButtonClasses} disabled:opacity-50 disabled:cursor-not-allowed`}
                                    >
                                        {isDownloading ? 'Creating Album...' : 'Download Album'}
                                    </button>
                                    <button 
                                        onClick={() => setIsShareModalOpen(true)}
                                        className="font-permanent-marker text-xl text-center text-white bg-slate-950 hover:bg-slate-900 border-2 border-yellow-400 py-3 px-8 rounded-sm transform transition-transform duration-200 hover:scale-105 -rotate-1 hover:border-yellow-300 shadow-[2px_2px_0px_2px_rgba(234,179,8,0.15)] flex items-center justify-center gap-2"
                                    >
                                        <Share2 className="h-5 w-5 text-yellow-400" />
                                        Share Multiverse
                                    </button>
                                    <button onClick={handleReset} className={secondaryButtonClasses}>
                                        Start Over
                                    </button>
                                </div>
                            )}
                        </div>

                        {uploadedImage && (
                            <ShareModal 
                                isOpen={isShareModalOpen}
                                onClose={() => setIsShareModalOpen(false)}
                                currentMode={currentMode}
                                generatedImages={generatedImages}
                                rejectedItems={rejectedItems}
                                uploadedImage={uploadedImage}
                            />
                        )}

                        <PinchToZoomOverlay 
                            isOpen={zoomImage !== null}
                            onClose={() => setZoomImage(null)}
                            imageUrl={zoomImage?.url || ''}
                            caption={zoomImage?.caption || ''}
                        />
                    </>
                )}
            </div>
            <Footer />
        </main>
    );
}

export default App;