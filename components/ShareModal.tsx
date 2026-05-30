import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Share2, Copy, Check, Download, X, Sparkles, Loader2, Link2, ExternalLink } from 'lucide-react';
import { cn } from '../lib/utils';
import { createAlbumPage } from '../lib/albumUtils';

interface ShareModalProps {
    isOpen: boolean;
    onClose: () => void;
    currentMode: 'decades' | 'parallel-worlds';
    generatedImages: Record<string, { status: string; url?: string; error?: string }>;
    rejectedItems: string[];
    uploadedImage: string;
}

// Helper to compress local uploaded image for deep link URL payload (max size ~2-3KB)
const compressOriginalImage = (base64Str: string, maxWidth = 120): Promise<string> => {
    return new Promise((resolve) => {
        const img = new Image();
        img.onload = () => {
            const canvas = document.createElement('canvas');
            const ratio = img.height / img.width;
            
            // Constrain limits to keep URL payload compact
            canvas.width = maxWidth;
            canvas.height = Math.round(maxWidth * ratio);
            
            const ctx = canvas.getContext('2d');
            if (!ctx) {
                resolve(base64Str);
                return;
            }
            
            // Draw original onto tiny sandbox canvas
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            
            // Return low-quality highly-compressed JPEG content data
            resolve(canvas.toDataURL('image/jpeg', 0.25));
        };
        img.onerror = () => {
            resolve(base64Str);
        };
        img.src = base64Str;
    });
};

export const ShareModal: React.FC<ShareModalProps> = ({
    isOpen,
    onClose,
    currentMode,
    generatedImages,
    rejectedItems,
    uploadedImage
}) => {
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [isCompiling, setIsCompiling] = useState(false);
    const [copied, setCopied] = useState(false);
    const [deepLink, setDeepLink] = useState('');
    const [errorMsg, setErrorMsg] = useState('');

    useEffect(() => {
        if (!isOpen) return;

        const generateStateLinkAndPreview = async () => {
            setIsCompiling(true);
            setErrorMsg('');
            try {
                // 1. Prepare valid images for grid collation
                const itemsList = currentMode === 'decades' 
                    ? ['1950s', '1960s', '1970s', '1980s', '1990s', '2000s']
                    : ['Cyberpunk', 'Fantasy', 'Noir', 'Steampunk', 'Space Opera', 'Post-Apocalyptic'];
                
                const activeItems = itemsList.filter(it => !rejectedItems.includes(it));
                
                const imageData: Record<string, string> = {};
                for (const item of itemsList) {
                    if (!rejectedItems.includes(item)) {
                        const image = generatedImages[item];
                        if (image && image.status === 'done' && image.url) {
                            imageData[item] = image.url;
                        }
                    }
                }

                if (Object.keys(imageData).length === 0) {
                    setErrorMsg("Please ensure you have generated at least one image before sharing.");
                    setIsCompiling(false);
                    return;
                }

                // 2. Compile image collage preview data URL
                const collagedUrl = await createAlbumPage(imageData);
                setPreviewUrl(collagedUrl);

                // 3. Compress original input image to fit in URL payload safely
                const compressedOriginal = await compressOriginalImage(uploadedImage, 120);

                // 4. Create current app state packet
                const statePacket = {
                    mode: currentMode,
                    rejected: rejectedItems,
                    original: compressedOriginal
                };

                // Convert state back to a compressed string structure
                const serialized = encodeURIComponent(JSON.stringify(statePacket));
                const shareableLink = `${window.location.origin}${window.location.pathname}#share=${serialized}`;
                setDeepLink(shareableLink);

            } catch (err) {
                console.error("Failed to compile share state:", err);
                setErrorMsg("Occurred an issue setting up the share matrix.");
            } finally {
                setIsCompiling(false);
            }
        };

        generateStateLinkAndPreview();
    }, [isOpen, currentMode, generatedImages, rejectedItems, uploadedImage]);

    const handleCopyLink = async () => {
        if (!deepLink) return;
        try {
            await navigator.clipboard.writeText(deepLink);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (err) {
            console.error("Failed to copy link:", err);
        }
    };

    const handleDownloadPreview = () => {
        if (!previewUrl) return;
        const link = document.createElement('a');
        link.href = previewUrl;
        link.download = 'multiverse-collection-share.jpg';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    const getXIntentUrl = () => {
        const text = `Check out my Past Forward multiverse collection! 🌌✨ Re-imagined across alternative eras and dimensions. Try it here:`;
        return `https://twitter.com/intent/tweet?text=${encodeURIComponent(text)}&url=${encodeURIComponent(deepLink)}`;
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
                    {/* Backdrop cover overlay */}
                    <motion.div 
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={onClose}
                        className="absolute inset-0 bg-black/90 backdrop-blur-sm"
                    />

                    {/* Pop-up dialog area */}
                    <motion.div 
                        initial={{ opacity: 0, scale: 0.9, y: 15 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.9, y: 15 }}
                        transition={{ type: "spring", damping: 25, stiffness: 350 }}
                        className="bg-neutral-900 border border-yellow-500/20 rounded-2xl p-6 md:p-8 w-full max-w-2xl shadow-2xl relative z-10 max-h-[90vh] overflow-y-auto flex flex-col"
                    >
                        {/* Close button indicator */}
                        <button 
                            onClick={onClose}
                            className="absolute top-4 right-4 text-neutral-400 hover:text-white transition-colors bg-white/5 p-2 rounded-full border border-white/5 hover:border-white/10"
                            id="close-share-modal"
                        >
                            <X className="h-4 w-4" />
                        </button>

                        <div className="flex items-center gap-2.5 mb-2">
                            <Sparkles className="h-5 w-5 text-yellow-400 animate-pulse" />
                            <span className="text-[10px] font-mono text-yellow-400 font-extrabold uppercase tracking-widest">DISPATCH SIGNAL</span>
                        </div>

                        <h2 className="text-3xl md:text-4xl font-permanent-marker text-neutral-100 mb-4">
                            Share Your Multiverse
                        </h2>

                        {errorMsg ? (
                            <div className="bg-red-950/20 border border-red-500/20 rounded-xl p-4 text-red-400 font-mono text-xs flex items-center gap-2">
                                <span>⚠️ {errorMsg}</span>
                            </div>
                        ) : isCompiling ? (
                            <div className="flex-1 flex flex-col items-center justify-center py-16 gap-4">
                                <Loader2 className="h-10 w-10 text-yellow-400 animate-spin" />
                                <span className="text-sm font-mono text-neutral-300">Compiling darkroom montage and state vector...</span>
                            </div>
                        ) : (
                            <div className="flex-1 flex flex-col gap-6">
                                {/* Grid split for share card preview vs action checklist */}
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
                                    {/* Left half: preview card of compiled collage */}
                                    <div className="flex flex-col items-center">
                                        <span className="text-[10px] font-mono text-neutral-400 uppercase tracking-wider mb-2 font-bold select-none">Compiled Montage Preview</span>
                                        {previewUrl ? (
                                            <div className="relative group rounded-xl border border-white/10 overflow-hidden shadow-lg w-full max-w-[240px] aspect-[1/1.4] bg-neutral-950/40 p-2">
                                                <img 
                                                    src={previewUrl} 
                                                    alt="Multiverse Preview Collage" 
                                                    className="w-full h-full object-cover rounded shadow"
                                                    referrerPolicy="no-referrer"
                                                />
                                                <button 
                                                    onClick={handleDownloadPreview}
                                                    className="absolute inset-0 bg-black/60 opacity-0 group-hover:opacity-100 flex flex-col items-center justify-center gap-2 transition-opacity duration-200 text-white font-permanent-marker text-sm"
                                                >
                                                    <Download className="h-6 w-6 text-yellow-400" />
                                                    Download Image
                                                </button>
                                            </div>
                                        ) : (
                                            <div className="w-full max-w-[240px] aspect-[1/1.4] bg-neutral-950/40 border border-dashed border-white/10 flex items-center justify-center rounded-xl text-xs text-neutral-500 font-mono">
                                                Failed compiling preview
                                            </div>
                                        )}
                                    </div>

                                    {/* Right half: instructions and action links */}
                                    <div className="flex flex-col justify-center gap-4">
                                        <div className="bg-black/30 border border-white/5 rounded-xl p-4">
                                            <h4 className="text-xs font-mono font-extrabold uppercase text-neutral-300 tracking-wider mb-2">💡 Quick Share Guide:</h4>
                                            <ol className="text-xs text-neutral-400 space-y-2 list-decimal list-inside leading-relaxed">
                                                <li>Click <span className="text-yellow-400 font-bold">Download Preview</span> below and save your custom photo collage.</li>
                                                <li>Click <span className="text-yellow-400 font-bold">Post to X</span> to launch Twitter with your custom deep link.</li>
                                                <li>Attach or paste your saved collage to show it off on your timeline!</li>
                                            </ol>
                                        </div>

                                        {/* Copyable link bar */}
                                        <div className="flex flex-col gap-1.5">
                                            <span className="text-[10px] font-mono text-neutral-500 uppercase tracking-wider font-extrabold">Deep Link State Vector</span>
                                            <div className="bg-neutral-950 border border-white/5 rounded-lg p-2.5 flex items-center justify-between gap-3 text-neutral-200 font-mono text-xs overflow-hidden">
                                                <span className="truncate pr-4 flex-1 select-all text-neutral-400">{deepLink}</span>
                                                <button 
                                                    onClick={handleCopyLink}
                                                    className={cn(
                                                        "p-1.5 rounded transition-all duration-200 shrink-0",
                                                        copied ? "bg-green-500/10 text-green-400" : "bg-white/5 text-neutral-300 hover:bg-white/10 hover:text-white"
                                                    )}
                                                    title="Copy Deep Link"
                                                >
                                                    {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Bottom action buttons pane */}
                                <div className="border-t border-white/5 pt-5 mt-2 flex flex-col sm:flex-row items-center gap-4">
                                    <button 
                                        onClick={handleDownloadPreview}
                                        className="w-full sm:flex-1 py-3 px-6 rounded-md bg-white/5 hover:bg-white/10 border border-white/10 text-white font-permanent-marker flex items-center justify-center gap-2.5 transition-all text-sm uppercase tracking-wider active:scale-95"
                                    >
                                        <Download className="h-4 w-4 text-neutral-300" />
                                        Download Preview
                                    </button>

                                    <a 
                                        href={getXIntentUrl()}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="w-full sm:flex-1 py-3 px-6 rounded-md bg-yellow-400 hover:bg-yellow-300 text-black font-permanent-marker flex items-center justify-center gap-2.5 transition-all text-sm uppercase tracking-wider active:scale-95 shadow-md"
                                    >
                                        <svg className="h-4 w-4 fill-current text-black" viewBox="0 0 24 24" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">
                                            <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                                        </svg>
                                        Post to X (Twitter)
                                        <ExternalLink className="h-3 w-3 opacity-60" />
                                    </a>
                                </div>
                            </div>
                        )}
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
};
