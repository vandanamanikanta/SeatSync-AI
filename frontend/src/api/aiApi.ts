import { apiClient } from './client';
import { ApiResponse, AiChatPayload, AiChatResponseData } from '../types';

export const aiApi = {
  /**
   * Send a query message to the Gemini AI Event Assistant
   */
  chat: async (payload: AiChatPayload): Promise<AiChatResponseData> => {
    const response = await apiClient.post<ApiResponse<AiChatResponseData>>('/api/ai/chat', payload, {
      timeout: 900000, // 15-minute timeout for live Gemini AI RAG queries
    });
    return response.data.data;
  },

  /**
   * Check if Gemini AI assistant is live/configured
   */
  getStatus: async (): Promise<boolean> => {
    try {
      const response = await apiClient.get<ApiResponse<unknown>>('/api/ai/status');
      const data = response.data.data as Record<string, unknown> | boolean;
      if (typeof data === 'boolean') return data;
      return Boolean(data?.liveGeminiActive);
    } catch {
      return false;
    }
  },
};

