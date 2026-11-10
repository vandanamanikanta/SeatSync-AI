import React from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';

interface ErrorMessageProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({
  title = 'Something went wrong',
  message,
  onRetry,
}) => {
  return (
    <div className="bg-rose-950/40 border border-rose-800/60 rounded-xl p-5 text-rose-200 my-4 flex items-start space-x-3.5 shadow-lg backdrop-blur-sm">
      <AlertCircle className="w-6 h-6 text-rose-400 flex-shrink-0 mt-0.5" />
      <div className="flex-1">
        <h4 className="font-semibold text-rose-300 text-sm">{title}</h4>
        <p className="text-sm text-rose-300/80 mt-1">{message}</p>
        {onRetry && (
          <button
            onClick={onRetry}
            className="mt-3 inline-flex items-center space-x-1.5 text-xs font-semibold bg-rose-900/60 hover:bg-rose-800/80 text-rose-100 px-3 py-1.5 rounded-lg border border-rose-700/50 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Try Again</span>
          </button>
        )}
      </div>
    </div>
  );
};

