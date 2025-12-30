# Guild of Smiths AI Agent Initialization

## Overview

The Guild of Smiths AI agent is a proactive, context-aware assistant that activates when the user presses the "Load" button in Settings > AI Assistant. This document describes the agent initialization sequence and capabilities.

## Agent States

The agent operates in three states:

1. **SLEEPING**: Agent not loaded, standard rule-based AI only
2. **WAKING**: Model loading and context initialization in progress
3. **ALIVE**: Agent fully active with context awareness and proactive behavior
4. **RULE_BASED_FALLBACK**: Agent using rule-based mode (when model load fails or battery constraints apply)

## Activation Sequence

### Step 1: User presses "Load" button
- Located in Settings > AI Assistant section
- Button appears when AI is enabled and model is downloaded but not loaded

### Step 2: Model Loading
```kotlin
// AIRouter.loadModel() is called
val result = LlamaInference.loadModel(modelPath)
if (result) {
    // Model loaded successfully
    AgentInitializer.wakeAgent(modelPath)
}
```

### Step 3: Agent Awakening
The agent initialization follows this sequence:

1. **Context Gathering** (20%): Collect user data from repositories
   - Job history from JobRepository
   - Message history from MessageRepository
   - Time entries from TimeEntryRepository
   - User preferences

2. **Memory Building** (40%): Analyze patterns and build context
   - Job patterns (common tasks, frequencies)
   - Communication patterns (peak hours, common phrases)
   - Time tracking patterns (work habits, break frequency)

3. **Proactive Setup** (80%): Initialize background behaviors
   - Ambient observation monitoring
   - Proactive suggestion engine
   - Context refresh scheduler

4. **Activation** (100%): Agent becomes alive and proactive

## Agent Capabilities

### When Alive (Full AI Mode)

#### 1. Enhanced Reasoning Loop
- Context-aware responses using user history
- Tool chaining for complex queries
- Occupational knowledge integration

#### 2. Proactive Behavior
- Ambient observation of all messages
- Contextual suggestions based on patterns
- Trade-specific assistance

#### 3. Tool Integration
- Web search for procedures/materials
- Weather API for outdoor work planning
- Code execution for calculations
- Integrated reasoning with battery/signal constraints

### When in Rule-Based Fallback
- Standard rule-based responses
- No context awareness
- Battery-efficient operation
- Always available

## Context Awareness

The agent builds comprehensive context from:

### Occupational Profiles
Pre-loaded trade knowledge:
- **Electrician**: Panel upgrades, circuits, safety protocols
- **HVAC**: System installation, refrigerant handling, load calculations
- **Plumber**: Pipe work, fixtures, pressure testing

### Learned Patterns
Dynamic analysis of user behavior:
- Common job types and frequencies
- Communication patterns and peak hours
- Time tracking habits and break patterns
- Material/tool usage patterns

### User Preferences
- AI mode (Standard/Hybrid)
- Gateway connectivity settings
- Personal workflow preferences

## Battery & Signal Constraints

The agent respects all platform constraints:

### Battery Preservation
- Falls back to rule-based when battery < 20%
- Monitors thermal status
- Avoids power-intensive operations

### Signal Reality
- Works offline with local models
- Queues responses for mesh sync
- Graceful degradation on poor connectivity

### Resource Discipline
- Small payload sizes for mesh
- Cached responses for offline sync
- Non-blocking UI operations

## Usage Examples

### Job Board Assistance
```
User: "Starting a panel upgrade job"
Agent: "Based on your history of 5 panel upgrades, recommend checking:
- 200A service capacity
- GFCI requirements for kitchen circuits
- NEC 2020 code compliance

Tools needed: Multimeter, wire strippers, voltage tester"
```

### Time Tracking
```
Agent observes: "taking lunch break"
Agent: "✓ Lunch break logged. Current session: 3h 45m.
Remember to clock back in within 30 minutes per OSHA guidelines."
```

### Proactive Suggestions
```
Based on weather API integration:
"⚠️ Heavy rain forecast for tomorrow. Consider rescheduling outdoor electrical work or preparing tarps/covers."
```

## Technical Architecture

### Core Components
- **AgentInitializer**: Handles wake/sleep lifecycle
- **AIRouter**: Routes requests through appropriate pipeline
- **AmbientObserver**: Passive message analysis
- **SubAgents**: Specialized assistants (Translator, TimeKeeper, etc.)
- **LlamaInference**: On-device LLM interface

### Data Flow
```
User presses Load → AIRouter.loadModel() → AgentInitializer.wakeAgent()
    ↓
Context Gathering → Memory Building → Proactive Setup → Agent Alive
    ↓
Ambient Observation + Enhanced Reasoning + Tool Integration
```

## Configuration

### Settings Integration
- AI toggle enables/disables all AI features
- AI Mode: Standard (rule-based) vs Hybrid (rule-based + external LLM)
- Battery auto-degrade settings
- Model download/management

### Model Management
- Qwen3-1.7B-Q4 recommended for mobile
- Automatic download from Hugging Face
- Local storage with cleanup options

## Error Handling

### Load Failures
- Model corruption → Redownload prompt
- Insufficient storage → Cleanup suggestions
- Battery too low → Wait for charging

### Runtime Failures
- LLM inference errors → Rule-based fallback
- Network timeouts → Local-only mode
- Memory pressure → Reduced context window

### Recovery
- Automatic fallback to rule-based mode
- User notification of status changes
- Manual reload option in settings

## Future Extensions

### Planned Features
- Dialect learning for trade-specific language
- Auto-quote engine with material pricing
- Regional pricing logic
- Disaster/offline resilience mode

### Tool Integrations
- Expanded weather APIs
- Material supplier databases
- Code compliance checkers
- Equipment rental systems

---

The agent represents a new paradigm for mobile AI: **proactive, context-aware, and resource-conscious**. It transforms the smartphone from a passive tool into an intelligent trade assistant that anticipates needs and provides expert guidance while respecting the constraints of mobile and field work environments.
