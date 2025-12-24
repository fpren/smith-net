import { useState, useEffect, useRef } from 'react';
import { Channel, Message, Presence, GatewayStatus } from './types';
import { wsClient } from './websocket';
import * as api from './api';

// ════════════════════════════════════════════════════════════════════
// STYLES (Console Theme)
// ════════════════════════════════════════════════════════════════════

const styles = {
  container: {
    display: 'flex',
    height: '100vh',
    background: '#0a0a0a',
    color: '#e0e0e0',
    fontFamily: "'Courier New', monospace",
  },
  sidebar: {
    width: '260px',
    borderRight: '1px solid #1a1a1a',
    display: 'flex',
    flexDirection: 'column' as const,
    background: '#0d0d0d',
  },
  header: {
    padding: '16px',
    borderBottom: '1px solid #1a1a1a',
  },
  brand: {
    fontSize: '18px',
    fontWeight: 'bold',
    letterSpacing: '2px',
  },
  version: {
    fontSize: '10px',
    color: '#666',
    marginLeft: '8px',
  },
  channelList: {
    flex: 1,
    overflow: 'auto',
    padding: '8px 0',
  },
  channelItem: {
    padding: '10px 16px',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  channelItemActive: {
    background: '#1a1a1a',
  },
  statusPanel: {
    padding: '12px 16px',
    borderTop: '1px solid #1a1a1a',
    fontSize: '11px',
  },
  main: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
  },
  chatHeader: {
    padding: '12px 20px',
    borderBottom: '1px solid #1a1a1a',
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  messages: {
    flex: 1,
    overflow: 'auto',
    padding: '16px 20px',
  },
  messageItem: {
    marginBottom: '12px',
  },
  messageSender: {
    fontSize: '12px',
    color: '#888',
    marginBottom: '2px',
  },
  messageContent: {
    fontSize: '14px',
  },
  messageOrigin: {
    fontSize: '10px',
    color: '#555',
    marginLeft: '8px',
  },
  inputBar: {
    padding: '12px 20px',
    borderTop: '1px solid #1a1a1a',
    display: 'flex',
    gap: '12px',
  },
  input: {
    flex: 1,
    background: '#1a1a1a',
    border: 'none',
    padding: '10px 14px',
    color: '#e0e0e0',
    fontFamily: "'Courier New', monospace",
    fontSize: '14px',
  },
  button: {
    background: '#1a1a1a',
    border: 'none',
    padding: '10px 16px',
    color: '#e0e0e0',
    fontFamily: "'Courier New', monospace",
    cursor: 'pointer',
  },
  buttonAccent: {
    color: '#4a9eff',
  },
  presence: {
    padding: '8px 16px',
    borderTop: '1px solid #1a1a1a',
    fontSize: '11px',
  },
  dot: {
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    display: 'inline-block',
  },
  loginContainer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100vh',
    flexDirection: 'column' as const,
    gap: '16px',
  },
};

// ════════════════════════════════════════════════════════════════════
// APP COMPONENT
// ════════════════════════════════════════════════════════════════════

export default function App() {
  const [userId, setUserId] = useState(() => localStorage.getItem('userId') || '');
  const [userName, setUserName] = useState(() => localStorage.getItem('userName') || '');
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [channels, setChannels] = useState<Channel[]>([]);
  const [activeChannel, setActiveChannel] = useState<Channel | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [presence, setPresence] = useState<Presence[]>([]);
  const [gateway, setGateway] = useState<GatewayStatus | null>(null);
  const [inputValue, setInputValue] = useState('');
  const [newChannelName, setNewChannelName] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const newChannelRef = useRef<HTMLInputElement>(null);
  const messageInputRef = useRef<HTMLInputElement>(null);

  // Login handler
  const handleLogin = async () => {
    const name = nameInputRef.current?.value || userName;
    console.log('[App] handleLogin called, name:', name);
    if (!name.trim()) {
      console.log('[App] name is empty, returning');
      return;
    }
    setUserName(name);

    const id = userId || crypto.randomUUID().substring(0, 8);
    localStorage.setItem('userName', name);
    localStorage.setItem('userId', id);
    setUserId(id);

    try {
      // Connect WebSocket (don't block on auth_ok - backend might not send channels)
      wsClient.connect(id, name).catch(e => console.log('[App] WS connect issue:', e));
      
      // Load ALL channels from API
      const allChannels = await api.getChannels();
      console.log('[App] Loaded channels:', allChannels);
      setChannels(allChannels);
      
      // Auto-select general channel if exists
      const general = allChannels.find((c: Channel) => c.name === 'general');
      if (general) {
        setActiveChannel(general);
      }
      
      setIsLoggedIn(true);

      // Load gateway status and presence
      try {
        const status = await api.getGatewayStatus();
        setGateway(status);
        
        const onlineUsers = await api.getOnlinePresence();
        setPresence(onlineUsers);
      } catch (e) {
        console.log('[App] Status fetch failed:', e);
      }
    } catch (e) {
      console.error('Login failed:', e);
    }
  };

  // Setup WebSocket handlers
  useEffect(() => {
    if (!isLoggedIn) return;

    wsClient.onMessage((msg) => {
      if (msg.channelId === activeChannel?.id) {
        setMessages(prev => [...prev, msg]);
      }
    });

    wsClient.onChannelCreated((ch) => {
      setChannels(prev => {
        if (prev.some(c => c.id === ch.id)) return prev;
        return [...prev, ch];
      });
    });

    wsClient.onChannelDeleted((id) => {
      setChannels(prev => prev.filter(c => c.id !== id));
      if (activeChannel?.id === id) {
        setActiveChannel(null);
        setMessages([]);
      }
    });

    wsClient.onChannelCleared((channelId) => {
      // If we're viewing the cleared channel, clear local messages
      if (activeChannel?.id === channelId) {
        setMessages([]);
      }
    });

    wsClient.onPresence((p) => {
      setPresence(p);
    });

    // Poll gateway status and presence every 10 seconds
    const interval = setInterval(async () => {
      try {
        const status = await api.getGatewayStatus();
        setGateway(status);
        
        // Also refresh presence
        const onlineUsers = await api.getOnlinePresence();
        setPresence(onlineUsers);
      } catch (e) {
        // ignore
      }
    }, 10000);

    return () => {
      clearInterval(interval);
      wsClient.disconnect();
    };
  }, [isLoggedIn, activeChannel]);

  // Load messages when channel changes + auto-refresh every 10s
  useEffect(() => {
    if (!activeChannel) return;

    // Initial load
    api.getMessages(activeChannel.id).then(setMessages).catch(console.error);

    // Auto-refresh messages every 10 seconds
    const refreshInterval = setInterval(() => {
      api.getMessages(activeChannel.id).then(setMessages).catch(console.error);
    }, 10000);

    return () => clearInterval(refreshInterval);
  }, [activeChannel]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Send message (online via WebSocket)
  const handleSend = async () => {
    const msg = messageInputRef.current?.value || inputValue;
    if (!msg.trim() || !activeChannel) return;

    try {
      // Send via API (stores and broadcasts)
      await api.sendMessage(activeChannel.id, msg);
      if (messageInputRef.current) messageInputRef.current.value = '';
      setInputValue('');
      
      // Refresh messages
      const updated = await api.getMessages(activeChannel.id);
      setMessages(updated);
    } catch (e) {
      console.error('Send failed:', e);
    }
  };

  // Create channel
  const handleCreateChannel = async () => {
    const name = newChannelRef.current?.value || newChannelName;
    console.log('[App] Creating channel:', name);
    if (!name.trim()) return;

    try {
      const channel = await api.createChannel(name, 'group');
      // Only add if not already in list (WebSocket might have already added it)
      setChannels(prev => {
        if (prev.some(c => c.id === channel.id)) return prev;
        return [...prev, channel];
      });
      if (newChannelRef.current) newChannelRef.current.value = '';
      setNewChannelName('');
    } catch (e) {
      console.error('Create channel failed:', e);
    }
  };

  // Format timestamp
  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
  };

  // Login screen
  if (!isLoggedIn) {
    return (
      <div style={styles.loginContainer}>
        <div style={styles.brand}>SMITH NET</div>
        <input
          ref={nameInputRef}
          style={{ ...styles.input, width: '200px' }}
          placeholder="Enter your name"
          defaultValue={userName}
          onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
        />
        <button style={{ ...styles.button, ...styles.buttonAccent }} onClick={handleLogin}>
          CONNECT
        </button>
      </div>
    );
  }

  // Main app
  return (
    <div style={styles.container}>
      {/* Sidebar */}
      <div style={styles.sidebar}>
        <div style={styles.header}>
          <span style={styles.brand}>SMITH NET</span>
          <span style={styles.version}>1.0</span>
        </div>

        <div style={styles.channelList}>
          {channels.map((ch) => (
            <div
              key={ch.id}
              style={{
                ...styles.channelItem,
                ...(activeChannel?.id === ch.id ? styles.channelItemActive : {}),
              }}
              onClick={() => setActiveChannel(ch)}
            >
              <span style={{ color: '#666' }}>{ch.type === 'dm' ? '@' : '#'}</span>
              <span>{ch.name}</span>
            </div>
          ))}

          {/* Create channel */}
          <div style={{ ...styles.channelItem, marginTop: '8px' }}>
            <input
              ref={newChannelRef}
              style={{ ...styles.input, flex: 1, padding: '6px 10px', fontSize: '12px' }}
              placeholder="+ new channel"
              defaultValue=""
              onKeyDown={(e) => e.key === 'Enter' && handleCreateChannel()}
            />
          </div>
        </div>

        {/* Presence */}
        <div style={styles.presence}>
          <div style={{ marginBottom: '6px', color: '#666' }}>ONLINE ({presence.length})</div>
          {presence.slice(0, 5).map((p) => (
            <div key={p.userId} style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
              <span style={{ ...styles.dot, background: '#4ade80' }} />
              <span>{p.userName}</span>
            </div>
          ))}
          
          {/* Show mesh peers from gateway */}
          {gateway?.relayConnected && gateway.relays && gateway.relays.length > 0 && (
            <>
              <div style={{ marginTop: '8px', marginBottom: '6px', color: '#666' }}>MESH RELAY</div>
              {gateway.relays.map((relay) => (
                <div key={relay.id} style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                  <span style={{ ...styles.dot, background: '#f59e0b' }} />
                  <span style={{ color: '#f59e0b' }}>{relay.name}</span>
                </div>
              ))}
            </>
          )}
        </div>

        {/* Gateway Status */}
        <div style={styles.statusPanel}>
          <div style={{ marginBottom: '6px', color: '#666' }}>GATEWAY RELAYS</div>
          {gateway?.relays && gateway.relays.length > 0 ? (
            gateway.relays.map((relay) => (
              <div key={relay.id} style={{ 
                display: 'flex', 
                alignItems: 'center', 
                gap: '6px', 
                marginBottom: '6px',
                padding: '4px 0'
              }}>
                <span style={{ ...styles.dot, background: '#4ade80' }} />
                <span style={{ flex: 1, fontSize: '12px' }}>{relay.name}</span>
                <button
                  style={{
                    background: '#2a1a1a',
                    border: '1px solid #4a2a2a',
                    color: '#f87171',
                    padding: '2px 8px',
                    fontSize: '10px',
                    cursor: 'pointer',
                    borderRadius: '3px',
                  }}
                  onClick={async () => {
                    if (confirm(`Disconnect ${relay.name} from gateway?`)) {
                      try {
                        await api.disconnectRelay(relay.id);
                        // Refresh gateway status
                        const status = await api.getGatewayStatus();
                        setGateway(status);
                      } catch (e) {
                        console.error('Disconnect failed:', e);
                      }
                    }
                  }}
                  title="Force disconnect this relay"
                >
                  KICK
                </button>
              </div>
            ))
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ ...styles.dot, background: '#666' }} />
              <span style={{ color: '#666' }}>No relays connected</span>
            </div>
          )}
        </div>
      </div>

      {/* Main content */}
      <div style={styles.main}>
        {activeChannel ? (
          <>
            <div style={styles.chatHeader}>
              <span style={{ color: '#666' }}>{activeChannel.type === 'dm' ? '@' : '#'}</span>
              <span style={{ fontWeight: 'bold' }}>{activeChannel.name}</span>
              <div style={{ marginLeft: 'auto', display: 'flex', gap: '8px' }}>
                {/* Connection status indicator */}
                <span style={{ 
                  fontSize: '10px', 
                  color: gateway?.relayConnected ? '#4ade80' : '#666',
                  alignSelf: 'center'
                }}>
                  {gateway?.relayConnected ? '● ONLINE+MESH' : '● ONLINE'}
                </span>
                {/* Clear chat button */}
                <button
                  style={{ 
                    ...styles.button, 
                    padding: '4px 10px', 
                    fontSize: '11px',
                    background: '#1a1a1a',
                    color: '#888'
                  }}
                  onClick={async () => {
                    if (confirm(`Clear all messages in #${activeChannel.name}? This affects everyone.`)) {
                      try {
                        await api.clearChannel(activeChannel.id);
                        setMessages([]);
                      } catch (e) {
                        console.error('Clear failed:', e);
                        alert('Failed to clear channel');
                      }
                    }
                  }}
                  title="Clear all messages in this channel"
                >
                  CLEAR
                </button>
              </div>
            </div>

            <div style={styles.messages}>
              {messages.map((msg) => (
                <div key={msg.id} style={styles.messageItem}>
                  <div style={styles.messageSender}>
                    {msg.senderName}
                    <span style={{ marginLeft: '8px', color: '#555' }}>{formatTime(msg.timestamp)}</span>
                    <span style={styles.messageOrigin}>[{msg.origin}]</span>
                  </div>
                  <div style={styles.messageContent}>{msg.content}</div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            <div style={styles.inputBar}>
              <input
                ref={messageInputRef}
                style={styles.input}
                placeholder="Type a message..."
                defaultValue=""
                onKeyDown={(e) => e.key === 'Enter' && handleSend()}
              />
              <button 
                style={{ ...styles.button, ...styles.buttonAccent }} 
                onClick={handleSend}
                title={gateway?.relayConnected 
                  ? "Send to everyone (online + mesh)" 
                  : "Send to online users only"
                }
              >
                SEND
              </button>
            </div>
          </>
        ) : (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#555' }}>
            Select a channel to start chatting
          </div>
        )}
      </div>
    </div>
  );
}
