import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import {
    Tooltip,
    TooltipContent,
    TooltipTrigger,
} from '@/components/ui/tooltip';
import { memo, ReactNode } from 'react';
import AsTextarea, {
    AsTextareaProps,
} from '@/components/chat/AsChat/textarea.tsx';

interface Props extends AsTextareaProps {
    children: ReactNode;
    /** Tooltip shown while the expanded textarea dialog is closed. */
    tooltip?: ReactNode;
}

const AsTextareaDialog = ({ children, tooltip, ...props }: Props) => {
    return (
        <Dialog>
            {/*
             * Both triggers run `asChild`, so the children are rendered as a
             * single <button>. Wrapping TooltipTrigger around DialogTrigger (or
             * the other way round) would otherwise nest a <button> inside a
             * <button>, which is invalid HTML.
             */}
            <Tooltip>
                <TooltipTrigger asChild>
                    <DialogTrigger asChild>{children}</DialogTrigger>
                </TooltipTrigger>
                {tooltip ? <TooltipContent>{tooltip}</TooltipContent> : null}
            </Tooltip>
            <DialogContent
                className="p-0 h-[calc(100vh-100px)]"
                showCloseButton={false}
            >
                <AsTextarea {...props} className="h-full" expandable={false} />
            </DialogContent>
        </Dialog>
    );
};

export default memo(AsTextareaDialog);
