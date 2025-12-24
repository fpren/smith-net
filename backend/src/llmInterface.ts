/**
 * C-04: Vendor-Neutral LLM Interface
 * 
 * Abstraction layer for AI/LLM providers.
 * Supports OpenAI, Anthropic, and local models.
 * Easy to add new providers without changing application code.
 */

// ════════════════════════════════════════════════════════════════════
// TYPES
// ════════════════════════════════════════════════════════════════════

export enum LLMProvider {
  OPENAI = 'openai',
  ANTHROPIC = 'anthropic',
  LOCAL = 'local',
  MOCK = 'mock', // For testing
}

export interface LLMMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface LLMCompletionRequest {
  messages: LLMMessage[];
  maxTokens?: number;
  temperature?: number;
  model?: string;
}

export interface LLMCompletionResponse {
  content: string;
  model: string;
  provider: LLMProvider;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
  latencyMs: number;
}

export interface LLMProviderConfig {
  provider: LLMProvider;
  apiKey?: string;
  baseUrl?: string;
  defaultModel?: string;
  maxTokens?: number;
  temperature?: number;
}

// ════════════════════════════════════════════════════════════════════
// PROVIDER INTERFACE
// ════════════════════════════════════════════════════════════════════

interface ILLMProvider {
  name: LLMProvider;
  complete(request: LLMCompletionRequest): Promise<LLMCompletionResponse>;
  isAvailable(): boolean;
}

// ════════════════════════════════════════════════════════════════════
// OPENAI PROVIDER
// ════════════════════════════════════════════════════════════════════

class OpenAIProvider implements ILLMProvider {
  name = LLMProvider.OPENAI;
  private apiKey: string;
  private baseUrl: string;
  private defaultModel: string;

  constructor(config: LLMProviderConfig) {
    this.apiKey = config.apiKey || process.env.OPENAI_API_KEY || '';
    this.baseUrl = config.baseUrl || 'https://api.openai.com/v1';
    this.defaultModel = config.defaultModel || 'gpt-3.5-turbo';
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  async complete(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    const startTime = Date.now();
    const model = request.model || this.defaultModel;

    const response = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.apiKey}`,
      },
      body: JSON.stringify({
        model,
        messages: request.messages,
        max_tokens: request.maxTokens || 1000,
        temperature: request.temperature ?? 0.7,
      }),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`OpenAI API error: ${error}`);
    }

    const data = await response.json();
    const latencyMs = Date.now() - startTime;

    return {
      content: data.choices[0]?.message?.content || '',
      model,
      provider: LLMProvider.OPENAI,
      usage: {
        promptTokens: data.usage?.prompt_tokens || 0,
        completionTokens: data.usage?.completion_tokens || 0,
        totalTokens: data.usage?.total_tokens || 0,
      },
      latencyMs,
    };
  }
}

// ════════════════════════════════════════════════════════════════════
// ANTHROPIC PROVIDER
// ════════════════════════════════════════════════════════════════════

class AnthropicProvider implements ILLMProvider {
  name = LLMProvider.ANTHROPIC;
  private apiKey: string;
  private baseUrl: string;
  private defaultModel: string;

  constructor(config: LLMProviderConfig) {
    this.apiKey = config.apiKey || process.env.ANTHROPIC_API_KEY || '';
    this.baseUrl = config.baseUrl || 'https://api.anthropic.com/v1';
    this.defaultModel = config.defaultModel || 'claude-3-haiku-20240307';
  }

  isAvailable(): boolean {
    return !!this.apiKey;
  }

  async complete(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    const startTime = Date.now();
    const model = request.model || this.defaultModel;

    // Convert messages format for Anthropic
    const systemMessage = request.messages.find(m => m.role === 'system')?.content || '';
    const chatMessages = request.messages
      .filter(m => m.role !== 'system')
      .map(m => ({ role: m.role, content: m.content }));

    const response = await fetch(`${this.baseUrl}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-api-key': this.apiKey,
        'anthropic-version': '2023-06-01',
      },
      body: JSON.stringify({
        model,
        system: systemMessage,
        messages: chatMessages,
        max_tokens: request.maxTokens || 1000,
        temperature: request.temperature ?? 0.7,
      }),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`Anthropic API error: ${error}`);
    }

    const data = await response.json();
    const latencyMs = Date.now() - startTime;

    return {
      content: data.content[0]?.text || '',
      model,
      provider: LLMProvider.ANTHROPIC,
      usage: {
        promptTokens: data.usage?.input_tokens || 0,
        completionTokens: data.usage?.output_tokens || 0,
        totalTokens: (data.usage?.input_tokens || 0) + (data.usage?.output_tokens || 0),
      },
      latencyMs,
    };
  }
}

// ════════════════════════════════════════════════════════════════════
// LOCAL PROVIDER (Ollama compatible)
// ════════════════════════════════════════════════════════════════════

class LocalProvider implements ILLMProvider {
  name = LLMProvider.LOCAL;
  private baseUrl: string;
  private defaultModel: string;

  constructor(config: LLMProviderConfig) {
    this.baseUrl = config.baseUrl || process.env.LOCAL_LLM_URL || 'http://localhost:11434';
    this.defaultModel = config.defaultModel || 'llama2';
  }

  isAvailable(): boolean {
    return !!this.baseUrl;
  }

  async complete(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    const startTime = Date.now();
    const model = request.model || this.defaultModel;

    // Format for Ollama API
    const prompt = request.messages
      .map(m => `${m.role}: ${m.content}`)
      .join('\n\n');

    const response = await fetch(`${this.baseUrl}/api/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model,
        prompt,
        stream: false,
        options: {
          temperature: request.temperature ?? 0.7,
          num_predict: request.maxTokens || 1000,
        },
      }),
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`Local LLM error: ${error}`);
    }

    const data = await response.json();
    const latencyMs = Date.now() - startTime;

    return {
      content: data.response || '',
      model,
      provider: LLMProvider.LOCAL,
      usage: {
        promptTokens: data.prompt_eval_count || 0,
        completionTokens: data.eval_count || 0,
        totalTokens: (data.prompt_eval_count || 0) + (data.eval_count || 0),
      },
      latencyMs,
    };
  }
}

// ════════════════════════════════════════════════════════════════════
// MOCK PROVIDER (For testing)
// ════════════════════════════════════════════════════════════════════

class MockProvider implements ILLMProvider {
  name = LLMProvider.MOCK;

  isAvailable(): boolean {
    return true;
  }

  async complete(request: LLMCompletionRequest): Promise<LLMCompletionResponse> {
    const startTime = Date.now();
    
    // Simulate API latency
    await new Promise(resolve => setTimeout(resolve, 100));

    const lastMessage = request.messages[request.messages.length - 1]?.content || '';
    const mockResponse = `[Mock LLM Response] Received: "${lastMessage.slice(0, 50)}..."`;

    return {
      content: mockResponse,
      model: 'mock-model',
      provider: LLMProvider.MOCK,
      usage: {
        promptTokens: lastMessage.split(' ').length,
        completionTokens: mockResponse.split(' ').length,
        totalTokens: lastMessage.split(' ').length + mockResponse.split(' ').length,
      },
      latencyMs: Date.now() - startTime,
    };
  }
}

// ════════════════════════════════════════════════════════════════════
// LLM MANAGER (Main Interface)
// ════════════════════════════════════════════════════════════════════

class LLMManager {
  private providers: Map<LLMProvider, ILLMProvider> = new Map();
  private defaultProvider: LLMProvider = LLMProvider.MOCK;
  private usageLog: Array<{
    timestamp: number;
    provider: LLMProvider;
    tokens: number;
    latencyMs: number;
  }> = [];

  constructor() {
    // Initialize with mock provider by default
    this.providers.set(LLMProvider.MOCK, new MockProvider());
    console.log('[LLM] Manager initialized with mock provider');
  }

  /**
   * Configure a provider with credentials.
   */
  configureProvider(config: LLMProviderConfig): void {
    let provider: ILLMProvider;

    switch (config.provider) {
      case LLMProvider.OPENAI:
        provider = new OpenAIProvider(config);
        break;
      case LLMProvider.ANTHROPIC:
        provider = new AnthropicProvider(config);
        break;
      case LLMProvider.LOCAL:
        provider = new LocalProvider(config);
        break;
      case LLMProvider.MOCK:
        provider = new MockProvider();
        break;
      default:
        throw new Error(`Unknown provider: ${config.provider}`);
    }

    this.providers.set(config.provider, provider);
    console.log(`[LLM] Configured provider: ${config.provider}`);

    // Set as default if it's available and we don't have a real provider yet
    if (provider.isAvailable() && this.defaultProvider === LLMProvider.MOCK) {
      this.defaultProvider = config.provider;
      console.log(`[LLM] Set default provider: ${config.provider}`);
    }
  }

  /**
   * Set the default provider.
   */
  setDefaultProvider(provider: LLMProvider): void {
    if (!this.providers.has(provider)) {
      throw new Error(`Provider not configured: ${provider}`);
    }
    this.defaultProvider = provider;
    console.log(`[LLM] Default provider set to: ${provider}`);
  }

  /**
   * Get list of available providers.
   */
  getAvailableProviders(): LLMProvider[] {
    return Array.from(this.providers.entries())
      .filter(([_, p]) => p.isAvailable())
      .map(([name]) => name);
  }

  /**
   * Complete a chat prompt using the default or specified provider.
   */
  async complete(
    request: LLMCompletionRequest,
    provider?: LLMProvider
  ): Promise<LLMCompletionResponse> {
    const targetProvider = provider || this.defaultProvider;
    const impl = this.providers.get(targetProvider);

    if (!impl) {
      throw new Error(`Provider not configured: ${targetProvider}`);
    }

    if (!impl.isAvailable()) {
      throw new Error(`Provider not available: ${targetProvider}`);
    }

    const response = await impl.complete(request);

    // Log usage
    this.usageLog.push({
      timestamp: Date.now(),
      provider: targetProvider,
      tokens: response.usage?.totalTokens || 0,
      latencyMs: response.latencyMs,
    });

    return response;
  }

  /**
   * Convenience method for simple prompts.
   */
  async prompt(
    userMessage: string,
    systemPrompt?: string,
    options?: { provider?: LLMProvider; maxTokens?: number; temperature?: number }
  ): Promise<string> {
    const messages: LLMMessage[] = [];
    
    if (systemPrompt) {
      messages.push({ role: 'system', content: systemPrompt });
    }
    messages.push({ role: 'user', content: userMessage });

    const response = await this.complete({
      messages,
      maxTokens: options?.maxTokens,
      temperature: options?.temperature,
    }, options?.provider);

    return response.content;
  }

  /**
   * Get usage statistics.
   */
  getUsageStats(): {
    totalRequests: number;
    totalTokens: number;
    avgLatencyMs: number;
    byProvider: Record<string, { requests: number; tokens: number }>;
  } {
    const byProvider: Record<string, { requests: number; tokens: number }> = {};
    let totalTokens = 0;
    let totalLatency = 0;

    for (const entry of this.usageLog) {
      totalTokens += entry.tokens;
      totalLatency += entry.latencyMs;

      if (!byProvider[entry.provider]) {
        byProvider[entry.provider] = { requests: 0, tokens: 0 };
      }
      byProvider[entry.provider].requests++;
      byProvider[entry.provider].tokens += entry.tokens;
    }

    return {
      totalRequests: this.usageLog.length,
      totalTokens,
      avgLatencyMs: this.usageLog.length > 0 ? totalLatency / this.usageLog.length : 0,
      byProvider,
    };
  }
}

// Export singleton instance
export const llm = new LLMManager();

// Auto-configure from environment variables
if (process.env.OPENAI_API_KEY) {
  llm.configureProvider({
    provider: LLMProvider.OPENAI,
    apiKey: process.env.OPENAI_API_KEY,
  });
}

if (process.env.ANTHROPIC_API_KEY) {
  llm.configureProvider({
    provider: LLMProvider.ANTHROPIC,
    apiKey: process.env.ANTHROPIC_API_KEY,
  });
}

console.log('[LLM] Interface initialized. Available providers:', llm.getAvailableProviders());
