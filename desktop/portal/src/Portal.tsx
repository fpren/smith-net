import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Channel, Message, Presence, GatewayStatus, MediaAttachment } from './types';
import { wsClient } from './websocket';
import * as api from './api';
import { supabaseChat } from './supabaseClient';
import { supabaseAuth } from './supabaseAuth';

/**
 * Smith Net Portal - Main chat interface
 */
export default function Portal() {
  const navigate = useNavigate();
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
  const [showCreateChannelModal, setShowCreateChannelModal] = useState(false);
  const [newChannelType, setNewChannelType] = useState<'group' | 'broadcast'>('group');
  const [selectedMembers, setSelectedMembers] = useState<string[]>([]);
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [allUsers, setAllUsers] = useState<Presence[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const newChannelRef = useRef<HTMLInputElement>(null);
  const messageInputRef = useRef<HTMLInputElement>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const recordingIntervalRef = useRef<number | null>(null);

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

  // Filter channels for privacy:
  // - Show channels created by this dashboard user
  // - Show public/broadcast channels (type !== 'dm')
  // - Hide private DMs unless dashboard created them
  const filterChannelsForDashboard = (allChannels: Channel[], dashboardUserId: string): Channel[] => {
    return allChannels.filter(ch => {
      // Always show channels created by this dashboard
      if (ch.creatorId === dashboardUserId) return true;

      // Show public/broadcast/group channels (not DMs)
      if (ch.type !== 'dm') return true;

      // Hide private DMs from other users
      return false;
    });
  };

  // Login handler
  const handleLogin = async () => {
    const name = nameInputRef.current?.value || userName;
    console.log('[Portal] handleLogin called, name:', name);
    if (!name.trim()) {
      console.log('[Portal] name is empty, returning');
      return;
    }
    setUserName(name);

    const id = userId || crypto.randomUUID().substring(0, 8);
    localStorage.setItem('userName', name);
    localStorage.setItem('userId', id);
    setUserId(id);

    try {
      // Connect to Supabase Realtime
      await supabaseChat.connect(id, name);

      // Also connect WebSocket for legacy backend support
      wsClient.connect(id, name).catch(e => console.log('[Portal] WS connect issue:', e));

      // Load channels from Supabase (already filtered for privacy)
      const visibleChannels = await supabaseChat.getChannels();
      console.log('[Portal] Visible channels:', visibleChannels.length, '(filtered for privacy)');
      setChannels(visibleChannels);

      // Auto-select general channel if exists
      const general = visibleChannels.find((c: Channel) => c.name === 'general');
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
        console.log('[Portal] Status fetch failed:', e);
      }

      // Load all users for channel member selection
      try {
        const users = await supabaseChat.getAllUsers();
        setAllUsers(users.map(u => ({
          userId: u.userId,
          userName: u.userName,
          status: (u.status || 'offline') as 'online' | 'away' | 'offline',
          lastSeen: Date.now(),
          connectionType: 'online' as const
        })));
      } catch (e) {
        console.log('[Portal] Users fetch failed:', e);
      }
    } catch (e) {
      console.error('Login failed:', e);
    }
  };

  // Setup Supabase real-time handlers (primary) + WebSocket (legacy fallback)
  useEffect(() => {
    if (!isLoggedIn) return;

    // Supabase real-time message handler (INSTANT - no polling!)
    supabaseChat.onMessage((msg) => {
      console.log('[Portal] Supabase message received:', msg.senderName, msg.channelId);
      if (msg.channelId === activeChannel?.id) {
        setMessages(prev => {
          // Avoid duplicates
          if (prev.some(m => m.id === msg.id)) return prev;
          return [...prev, msg];
        });
      }
    });

    // Legacy WebSocket handler (fallback)
    wsClient.onMessage((msg) => {
      if (msg.channelId === activeChannel?.id) {
        setMessages(prev => {
          if (prev.some(m => m.id === msg.id)) return prev;
          return [...prev, msg];
        });
      }
    });

    wsClient.onChannelCreated((ch) => {
      // Only add if it passes privacy filter
      // (dashboard-created OR public/group channel)
      const isVisible = ch.creatorId === userId || ch.type !== 'dm';
      if (!isVisible) {
        console.log('[Portal] Ignoring private channel:', ch.name);
        return;
      }

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

    // Poll gateway status every 10 seconds (legacy backend)
    const interval = setInterval(async () => {
      try {
        const status = await api.getGatewayStatus();
        setGateway(status);
      } catch (e) {
        // ignore - legacy backend may not be running
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

    // Initial load from Supabase
    supabaseChat.getChannelMessages(activeChannel.id).then(setMessages).catch(console.error);

    // Auto-refresh messages every 10 seconds
    const refreshInterval = setInterval(() => {
      supabaseChat.getChannelMessages(activeChannel.id).then(setMessages).catch(console.error);
    }, 10000);

    return () => clearInterval(refreshInterval);
  }, [activeChannel]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Send message (via Supabase Realtime)
  const handleSend = async () => {
    const msg = messageInputRef.current?.value || inputValue;
    if (!msg.trim() || !activeChannel) return;

    try {
      // Send via Supabase (stores and broadcasts to all subscribers)
      await supabaseChat.sendMessage(activeChannel.id, msg);
      if (messageInputRef.current) messageInputRef.current.value = '';
      setInputValue('');

      // Refresh messages
      const updated = await supabaseChat.getChannelMessages(activeChannel.id);
      setMessages(updated);
    } catch (e) {
      console.error('Send failed:', e);
    }
  };

  // Create channel (via Supabase - dashboard owns it)
  const handleCreateChannel = async (name?: string, type?: 'group' | 'broadcast', members?: string[]) => {
    const channelName = name || newChannelRef.current?.value || newChannelName;
    const channelType = type || newChannelType;
    const channelMembers = members || selectedMembers;

    console.log('[Portal] Creating channel:', channelName, 'type:', channelType, 'members:', channelMembers);
    if (!channelName.trim()) return;

    try {
      // Create via Supabase - this dashboard will own the channel
      const channel = await supabaseChat.createChannel(channelName, channelType);
      // Only add if not already in list
      setChannels(prev => {
        if (prev.some(c => c.id === channel.id)) return prev;
        return [...prev, channel];
      });
      if (newChannelRef.current) newChannelRef.current.value = '';
      setNewChannelName('');
      setShowCreateChannelModal(false);
      setSelectedMembers([]);
      setNewChannelType('group');

      // Also notify legacy backend
      api.createChannel(channelName, channelType, channelMembers).catch(e => console.log('[Portal] Legacy channel create:', e));
    } catch (e) {
      console.error('Create channel failed:', e);
    }
  };

  // Toggle member selection
  const toggleMember = (userId: string) => {
    setSelectedMembers(prev =>
      prev.includes(userId)
        ? prev.filter(id => id !== userId)
        : [...prev, userId]
    );
  };

  // Filter users by search query
  const filteredUsers = allUsers.filter(u =>
    u.userName.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
    u.userId.toLowerCase().includes(userSearchQuery.toLowerCase())
  );

  // Format timestamp
  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
  };

  // Format file size
  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  // Start voice recording
  const startRecording = async () => {
    if (!activeChannel) return;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) {
          audioChunksRef.current.push(e.data);
        }
      };

      mediaRecorder.onstop = async () => {
        // Stop all tracks
        stream.getTracks().forEach(track => track.stop());

        // Create audio blob
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
        const duration = recordingTime;

        // Reset recording state
        setRecordingTime(0);
        if (recordingIntervalRef.current) {
          clearInterval(recordingIntervalRef.current);
          recordingIntervalRef.current = null;
        }

        // Upload
        setIsUploading(true);
        try {
          const file = new File([audioBlob], `voice_${Date.now()}.webm`, { type: 'audio/webm' });
          const media = await supabaseChat.uploadFile(file, activeChannel.id);
          media.duration = duration; // Add duration

          await supabaseChat.sendMessage(
            activeChannel.id,
            `[VOICE] ${duration}s`,
            media
          );

          // Add to local messages
          const newMessage: Message = {
            id: crypto.randomUUID(),
            channelId: activeChannel.id,
            senderId: supabaseChat.getUserId(),
            senderName: userName,
            content: `[VOICE] ${duration}s`,
            timestamp: Date.now(),
            origin: 'online',
            media: { ...media, duration },
          };
          setMessages(prev => [...prev, newMessage]);

          console.log('[Portal] Voice memo sent');
        } catch (err) {
          console.error('[Portal] Voice upload failed:', err);
          alert('Failed to send voice memo: ' + (err as Error).message);
        } finally {
          setIsUploading(false);
        }
      };

      mediaRecorder.start(100); // Collect data every 100ms
      setIsRecording(true);

      // Start timer
      recordingIntervalRef.current = window.setInterval(() => {
        setRecordingTime(prev => prev + 1);
      }, 1000);

      console.log('[Portal] Recording started');
    } catch (err) {
      const error = err as Error;
      console.error('[Portal] Failed to start recording:', error.name, error.message);

      if (error.name === 'NotAllowedError') {
        alert('Microphone access denied.\n\nPlease allow microphone access in your browser settings and try again.');
      } else if (error.name === 'NotFoundError') {
        alert('No microphone found.\n\nPlease connect a microphone and try again.');
      } else if (error.name === 'NotReadableError') {
        alert('Microphone is in use by another application.\n\nPlease close other apps using the microphone and try again.');
      } else {
        alert('Failed to access microphone:\n' + error.message);
      }
    }
  };

  // Stop voice recording
  const stopRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);
      console.log('[Portal] Recording stopped');
    }
  };

  // Cancel voice recording
  const cancelRecording = () => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      audioChunksRef.current = []; // Clear chunks so nothing uploads
      setIsRecording(false);
      setRecordingTime(0);
      if (recordingIntervalRef.current) {
        clearInterval(recordingIntervalRef.current);
        recordingIntervalRef.current = null;
      }
      console.log('[Portal] Recording cancelled');
    }
  };

  // Handle file selection and upload
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !activeChannel) return;

    // Reset input
    e.target.value = '';

    // Check file size (max 50MB)
    const maxSize = 50 * 1024 * 1024;
    if (file.size > maxSize) {
      alert('File too large. Maximum size is 50MB.');
      return;
    }

    setIsUploading(true);
    console.log('[Portal] Uploading file:', file.name, 'size:', file.size);

    try {
      // Upload to Supabase Storage
      const media = await supabaseChat.uploadFile(file, activeChannel.id);

      // Send message with media attachment
      const caption = messageInputRef.current?.value || '';
      await supabaseChat.sendMessage(
        activeChannel.id,
        caption || `[${media.type.toUpperCase()}] ${file.name}`,
        media
      );

      // Clear input
      if (messageInputRef.current) {
        messageInputRef.current.value = '';
      }

      // Add to local messages (optimistic update)
      const newMessage: Message = {
        id: crypto.randomUUID(),
        channelId: activeChannel.id,
        senderId: supabaseChat.getUserId(),
        senderName: userName,
        content: caption || `[${media.type.toUpperCase()}] ${file.name}`,
        timestamp: Date.now(),
        origin: 'online',
        media,
      };
      setMessages(prev => [...prev, newMessage]);

      console.log('[Portal] File sent successfully');
    } catch (err) {
      console.error('[Portal] File upload failed:', err);
      const errorMsg = (err as Error).message || 'Unknown error';

      // Check for common Supabase storage errors
      if (errorMsg.includes('Bucket not found') || errorMsg.includes('bucket')) {
        alert('Storage not configured.\n\nPlease run the Supabase migration to create the "media" bucket.\n\nSee: supabase-migrations/002_add_media_support.sql');
      } else if (errorMsg.includes('column') || errorMsg.includes('media_type')) {
        alert('Database not configured.\n\nPlease run the Supabase migration to add media columns.\n\nSee: supabase-migrations/002_add_media_support.sql');
      } else {
        alert('Failed to upload file:\n' + errorMsg);
      }
    } finally {
      setIsUploading(false);
    }
  };

  // Check authentication on mount
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const session = await supabaseAuth.getSession();
        if (session?.user) {
          // User is authenticated, proceed to portal
          setIsLoggedIn(true);
          // You might want to get user profile data here
        } else {
          // Not authenticated, redirect to auth
          navigate('/');
        }
      } catch (error) {
        console.error('Auth check failed:', error);
        navigate('/');
      }
    };

    checkAuth();
  }, [navigate]);

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

  // Main portal interface
  return (
    <div style={styles.container}>
      {/* Sidebar */}
      <div style={styles.sidebar}>
        <div style={styles.header}>
          <span style={styles.brand}>SMITH NET</span>
          <span style={styles.version}>1.0</span>
        </div>

        <div style={styles.channelList}>
          {channels.map((ch) => {
            const isOwned = ch.creatorId === userId;
            return (
            <div
              key={ch.id}
              style={{
                ...styles.channelItem,
                ...(activeChannel?.id === ch.id ? styles.channelItemActive : {}),
              }}
              onClick={() => setActiveChannel(ch)}
                title={isOwned ? 'You created this channel (click × to delete)' : `Created by ${ch.creatorId}`}
            >
                <span style={{ color: isOwned ? '#4a9eff' : '#666' }}>
                  {ch.type === 'dm' ? '@' : '#'}
                </span>
              <span style={{ flex: 1 }}>{ch.name}</span>
                {isOwned && (
                  <>
                    <span style={{ color: '#4a9eff', fontSize: '10px' }}>★</span>
                    <span
                      style={{
                        color: '#f87171',
                        fontSize: '14px',
                        marginLeft: '8px',
                        cursor: 'pointer',
                        padding: '0 4px',
                      }}
                      onClick={async (e) => {
                        e.stopPropagation(); // Don't select the channel
                        if (confirm(`Delete channel #${ch.name}? This cannot be undone.`)) {
                          try {
                            await supabaseChat.deleteChannel(ch.id);
                            // Remove from local state
                            setChannels(prev => prev.filter(c => c.id !== ch.id));
                            // Clear active channel if it was this one
                            if (activeChannel?.id === ch.id) {
                              setActiveChannel(null);
                              setMessages([]);
                            }
                          } catch (e) {
                            console.error('Delete failed:', e);
                            alert('Failed to delete channel: ' + (e as Error).message);
                          }
                        }
                      }}
                      title="Delete this channel"
                    >
                      ×
                    </span>
                  </>
                )}
            </div>
            );
          })}

          {/* Create channel button */}
          <div
            style={{
              ...styles.channelItem,
              marginTop: '8px',
              cursor: 'pointer',
              color: '#4a9eff'
            }}
            onClick={() => setShowCreateChannelModal(true)}
          >
            <span>+</span>
            <span>New Channel</span>
          </div>
        </div>

        {/* Create Channel Modal */}
        {showCreateChannelModal && (
          <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,0.8)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000
          }}>
            <div style={{
              background: '#0d0d0d',
              border: '1px solid #333',
              borderRadius: '8px',
              padding: '24px',
              width: '400px',
              maxHeight: '80vh',
              overflow: 'auto'
            }}>
              <h3 style={{ margin: '0 0 20px 0', color: '#4a9eff' }}>Create New Channel</h3>

              {/* Channel name */}
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', marginBottom: '6px', color: '#888', fontSize: '12px' }}>
                  CHANNEL NAME
                </label>
                <input
                  ref={newChannelRef}
                  style={{ ...styles.input, width: '100%', boxSizing: 'border-box' }}
                  placeholder="e.g., job-site-bravo"
                  defaultValue=""
                  autoFocus
                />
              </div>

              {/* Channel type */}
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', marginBottom: '6px', color: '#888', fontSize: '12px' }}>
                  CHANNEL TYPE
                </label>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <label style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    cursor: 'pointer',
                    color: newChannelType === 'group' ? '#4a9eff' : '#888'
                  }}>
                    <input
                      type="radio"
                      name="channelType"
                      checked={newChannelType === 'group'}
                      onChange={() => setNewChannelType('group')}
                    />
                    Group (private)
                  </label>
                  <label style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    cursor: 'pointer',
                    color: newChannelType === 'broadcast' ? '#4a9eff' : '#888'
                  }}>
                    <input
                      type="radio"
                      name="channelType"
                      checked={newChannelType === 'broadcast'}
                      onChange={() => setNewChannelType('broadcast')}
                    />
                    Broadcast (public)
                  </label>
                </div>
              </div>

              {/* User search and selection (for group channels) */}
              {newChannelType === 'group' && (
                <div style={{ marginBottom: '16px' }}>
                  <label style={{ display: 'block', marginBottom: '6px', color: '#888', fontSize: '12px' }}>
                    ADD MEMBERS ({selectedMembers.length} selected)
                  </label>
                  <input
                    style={{ ...styles.input, width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
                    placeholder="Search users..."
                    value={userSearchQuery}
                    onChange={(e) => setUserSearchQuery(e.target.value)}
                  />
                  <div style={{
                    maxHeight: '150px',
                    overflow: 'auto',
                    border: '1px solid #333',
                    borderRadius: '4px'
                  }}>
                    {filteredUsers.length === 0 ? (
                      <div style={{ padding: '12px', color: '#666', textAlign: 'center' }}>
                        {allUsers.length === 0 ? 'No users available' : 'No matching users'}
                      </div>
                    ) : (
                      filteredUsers.map(user => (
                        <div
                          key={user.userId}
                          style={{
                            padding: '8px 12px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            cursor: 'pointer',
                            background: selectedMembers.includes(user.userId) ? '#1a2a3a' : 'transparent',
                            borderBottom: '1px solid #222'
                          }}
                          onClick={() => toggleMember(user.userId)}
                        >
                          <span style={{
                            ...styles.dot,
                            background: user.status === 'online' ? '#4ade80' : '#666'
                          }} />
                          <span style={{ flex: 1 }}>{user.userName}</span>
                          {selectedMembers.includes(user.userId) && (
                            <span style={{ color: '#4a9eff' }}>✓</span>
                          )}
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}

              {/* Actions */}
              <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end', marginTop: '20px' }}>
                <button
                  style={{ ...styles.button, color: '#888' }}
                  onClick={() => {
                    setShowCreateChannelModal(false);
                    setSelectedMembers([]);
                    setUserSearchQuery('');
                  }}
                >
                  CANCEL
                </button>
                <button
                  style={{ ...styles.button, ...styles.buttonAccent }}
                  onClick={() => handleCreateChannel()}
                >
                  CREATE CHANNEL
                </button>
              </div>
            </div>
          </div>
        )}

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
                  {/* Text content */}
                  {msg.content && <div style={styles.messageContent}>{msg.content}</div>}

                  {/* Media attachment rendering */}
                  {msg.media && (
                    <div style={{ marginTop: '8px' }}>
                      {/* Image */}
                      {msg.media.type === 'image' && (
                        <div
                          style={{
                            maxWidth: '300px',
                            borderRadius: '8px',
                            overflow: 'hidden',
                            cursor: 'pointer',
                            border: '1px solid #333'
                          }}
                          onClick={() => window.open(msg.media?.url, '_blank')}
                          title="Click to view full size"
                        >
                          <img
                            src={msg.media.url}
                            alt={msg.media.filename || 'Image'}
                            style={{ width: '100%', display: 'block' }}
                            onError={(e) => {
                              (e.target as HTMLImageElement).style.display = 'none';
                              (e.target as HTMLImageElement).parentElement!.innerHTML =
                                '<div style="padding: 12px; color: #888;">📷 Image unavailable</div>';
                            }}
                          />
                        </div>
                      )}

                      {/* Voice message */}
                      {msg.media.type === 'voice' && (
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '8px',
                          background: '#1a1a1a',
                          padding: '8px 12px',
                          borderRadius: '4px',
                          border: '1px solid #333',
                          maxWidth: '300px'
                        }}>
                          <span style={{ fontSize: '11px', color: '#4a9eff', fontFamily: "'Courier New', monospace" }}>[VOICE]</span>
                          <audio
                            controls
                            src={msg.media.url}
                            style={{ height: '32px', flex: 1 }}
                          >
                            Your browser does not support audio.
                          </audio>
                          {msg.media.duration && (
                            <span style={{ color: '#666', fontSize: '11px', fontFamily: "'Courier New', monospace" }}>
                              {Math.floor(msg.media.duration / 60)}:{String(Math.floor(msg.media.duration % 60)).padStart(2, '0')}
                            </span>
                          )}
                        </div>
                      )}

                      {/* Video */}
                      {msg.media.type === 'video' && (
                        <div style={{
                          maxWidth: '400px',
                          borderRadius: '8px',
                          overflow: 'hidden',
                          border: '1px solid #333'
                        }}>
                          <video
                            controls
                            src={msg.media.url}
                            poster={msg.media.thumbnailUrl}
                            style={{ width: '100%', display: 'block' }}
                          >
                            Your browser does not support video.
                          </video>
                        </div>
                      )}

                      {/* File attachment */}
                      {msg.media.type === 'file' && (
                        <a
                          href={msg.media.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            background: '#1a1a1a',
                            padding: '10px 14px',
                            borderRadius: '4px',
                            border: '1px solid #333',
                            textDecoration: 'none',
                            color: '#4a9eff',
                            maxWidth: '300px',
                            fontFamily: "'Courier New', monospace"
                          }}
                        >
                          <span style={{ fontSize: '11px', color: '#4a9eff' }}>[FILE]</span>
                          <div style={{ flex: 1, overflow: 'hidden' }}>
                            <div style={{
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              fontSize: '12px'
                            }}>
                              {msg.media.filename || 'Download'}
                            </div>
                            {msg.media.size && (
                              <div style={{ color: '#666', fontSize: '10px' }}>
                                {formatFileSize(msg.media.size)}
                              </div>
                            )}
                          </div>
                          <span style={{ color: '#666', fontSize: '11px' }}>▼</span>
                        </a>
                      )}
                    </div>
                  )}
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            <div style={styles.inputBar}>
              {/* Hidden file inputs */}
              <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                accept="*/*"
                onChange={handleFileSelect}
              />
              <input
                type="file"
                ref={imageInputRef}
                style={{ display: 'none' }}
                accept="image/*,video/*"
                onChange={handleFileSelect}
              />

              {/* Media buttons - Smith Net console style */}
              <div style={{ display: 'flex', gap: '4px', marginRight: '8px' }}>
                <button
                  style={{
                    ...styles.button,
                    padding: '6px 10px',
                    fontSize: '12px',
                    fontFamily: "'Courier New', monospace",
                    background: isUploading ? '#333' : '#1a1a1a',
                    border: '1px solid #333',
                    opacity: isUploading ? 0.5 : 1,
                    color: '#4a9eff'
                  }}
                  onClick={() => imageInputRef.current?.click()}
                  disabled={isUploading || isRecording}
                  title="Send image or video"
                >
                  [IMG]
                </button>
                <button
                  style={{
                    ...styles.button,
                    padding: '6px 10px',
                    fontSize: '12px',
                    fontFamily: "'Courier New', monospace",
                    background: isUploading ? '#333' : '#1a1a1a',
                    border: '1px solid #333',
                    opacity: isUploading ? 0.5 : 1,
                    color: '#4a9eff'
                  }}
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isUploading || isRecording}
                  title="Send file"
                >
                  [FILE]
                </button>
                {/* Voice memo button */}
                {!isRecording ? (
                  <button
                    style={{
                      ...styles.button,
                      padding: '6px 10px',
                      fontSize: '12px',
                      fontFamily: "'Courier New', monospace",
                      background: isUploading ? '#333' : '#1a1a1a',
                      border: '1px solid #333',
                      opacity: isUploading ? 0.5 : 1,
                      color: '#f59e0b'
                    }}
                    onClick={startRecording}
                    disabled={isUploading}
                    title="Record voice memo (hold to record)"
                  >
                    [MIC]
                  </button>
                ) : (
                  <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                    <span style={{
                      color: '#f87171',
                      fontSize: '12px',
                      fontFamily: "'Courier New', monospace",
                      animation: 'pulse 1s infinite'
                    }}>
                      ● REC {recordingTime}s
                    </span>
                    <button
                      style={{
                        ...styles.button,
                        padding: '6px 10px',
                        fontSize: '12px',
                        fontFamily: "'Courier New', monospace",
                        background: '#2a1a1a',
                        border: '1px solid #4ade80',
                        color: '#4ade80'
                      }}
                      onClick={stopRecording}
                      title="Send voice memo"
                    >
                      [SEND]
                    </button>
                    <button
                      style={{
                        ...styles.button,
                        padding: '6px 10px',
                        fontSize: '12px',
                        fontFamily: "'Courier New', monospace",
                        background: '#2a1a1a',
                        border: '1px solid #f87171',
                        color: '#f87171'
                      }}
                      onClick={cancelRecording}
                      title="Cancel recording"
                    >
                      [X]
                    </button>
                  </div>
                )}
              </div>

              {/* Text input */}
              <input
                ref={messageInputRef}
                style={styles.input}
                placeholder={isUploading ? "Uploading..." : "Type a message..."}
                defaultValue=""
                onKeyDown={(e) => e.key === 'Enter' && handleSend()}
                disabled={isUploading}
              />

              {/* Send button */}
              <button
                style={{
                  ...styles.button,
                  ...styles.buttonAccent,
                  opacity: isUploading ? 0.5 : 1
                }}
                onClick={handleSend}
                disabled={isUploading}
                title={gateway?.relayConnected
                  ? "Send to everyone (online + mesh)"
                  : "Send to online users only"
                }
              >
                {isUploading ? '...' : 'SEND'}
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
