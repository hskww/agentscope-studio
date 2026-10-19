import React from 'react';
import { Mic, Square } from 'lucide-react';
import { cn } from '@/lib/utils';

// Native button props are forwarded, so that a wrapping Radix trigger
// (`<TooltipTrigger asChild>` / `<DialogTrigger asChild>` ...) can hand its
// event handlers and ref down to the real <button>.
export type VoiceButtonProps = Omit<
    React.ComponentProps<'button'>,
    'onClick' | 'size'
> & {
    isListening: boolean;
    onClick?: () => void;
    size?: 'sm' | 'md' | 'lg';
};

const VoiceButton = ({
    isListening,
    onClick,
    disabled = false,
    size = 'sm',
    className = '',
    ...props
}: VoiceButtonProps) => {
    const sizeClasses = {
        sm: 'w-6 h-6',
        md: 'w-12 h-12',
        lg: 'w-16 h-16',
    };

    const iconSizes = {
        sm: 16,
        md: 24,
        lg: 32,
    };

    return (
        <button
            onClick={onClick}
            disabled={disabled}
            {...props}
            className={cn(
                'relative rounded-full flex items-center justify-center transition-all duration-300 ease-in-out',
                sizeClasses[size],
                {
                    'bg-red-500 hover:bg-red-600 animate-pulse shadow-lg shadow-red-500/50':
                        isListening,
                    'bg-blue-500 hover:bg-blue-600 shadow-lg shadow-blue-500/30':
                        !isListening,
                },
                {
                    'opacity-50 cursor-not-allowed': disabled,
                    'cursor-pointer hover:scale-110': !disabled,
                },
                className,
            )}
            aria-label={isListening ? 'Stop recording' : 'Start recording'}
        >
            {/* Ripple effect */}
            {isListening && (
                <>
                    <span className="absolute inset-0 rounded-full bg-red-500 animate-ping opacity-75"></span>
                    <span className="absolute inset-0 rounded-full bg-red-500 animate-pulse opacity-50"></span>
                </>
            )}

            {/* Icon */}
            {isListening ? (
                <Square
                    className={cn('text-white relative z-10')}
                    size={iconSizes[size] * 0.7}
                    strokeWidth={3}
                />
            ) : (
                <Mic
                    className={cn('text-white relative z-10')}
                    size={iconSizes[size]}
                />
            )}
        </button>
    );
};

export default VoiceButton;
