import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import { SeatUpdateWsMessage } from '../types';

interface UseSeatWebSocketOptions {
  eventId: number;
  onSeatUpdate: (update: SeatUpdateWsMessage) => void;
  onReconnect?: () => void;
}

export function useSeatWebSocket({ eventId, onSeatUpdate, onReconnect }: UseSeatWebSocketOptions) {
  const [isConnected, setIsConnected] = useState(false);
  const [connectionError, setConnectionError] = useState<string | null>(null);
  const clientRef = useRef<Client | null>(null);
  const hasConnectedOnceRef = useRef(false);

  // Stable callback ref
  const onSeatUpdateRef = useRef(onSeatUpdate);
  onSeatUpdateRef.current = onSeatUpdate;

  const onReconnectRef = useRef(onReconnect);
  onReconnectRef.current = onReconnect;

  const connect = useCallback(() => {
    if (!eventId) return;

    const token = localStorage.getItem('seatsync_access_token') || '';
    if (!token) {
      setConnectionError('Authentication token missing for live updates');
      return;
    }

    // Determine WebSocket Broker URL
    const baseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8081';
    const brokerUrl = baseUrl.replace(/^http/, 'ws') + '/ws-seatsync';

    const client = new Client({
      brokerURL: brokerUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (msg) => {
        if (import.meta.env.DEV) {
          // console.debug('[STOMP]', msg);
        }
      },
    });

    client.onConnect = () => {
      setIsConnected(true);
      setConnectionError(null);

      // If reconnecting after a disconnect, invoke recovery callback to refetch matrix
      if (hasConnectedOnceRef.current) {
        onReconnectRef.current?.();
      }
      hasConnectedOnceRef.current = true;

      // Subscribe to event's real-time seat status topic
      const topic = `/topic/events/${eventId}/seats`;
      client.subscribe(topic, (message: IMessage) => {
        try {
          const update: SeatUpdateWsMessage = JSON.parse(message.body);
          onSeatUpdateRef.current(update);
        } catch (e) {
          console.error('[WebSocket] Failed to parse seat update message', e);
        }
      });
    };

    client.onDisconnect = () => {
      setIsConnected(false);
    };

    client.onStompError = (frame) => {
      console.warn('[WebSocket] STOMP error frame:', frame.headers['message']);
      setConnectionError(frame.headers['message'] || 'WebSocket communication error');
    };

    client.onWebSocketError = (event) => {
      console.warn('[WebSocket] Transport error:', event);
      setConnectionError('WebSocket connection error. Retrying in background...');
    };

    client.activate();
    clientRef.current = client;
  }, [eventId]);

  useEffect(() => {
    connect();

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
        setIsConnected(false);
      }
    };
  }, [connect]);

  return { isConnected, connectionError };
}

