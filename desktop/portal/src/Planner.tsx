import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { supabaseAuth } from './supabaseAuth';

/**
 * Guild of Smiths Planner Component
 * Planning interface for job management and scheduling
 */

interface Plan {
  id: string;
  title: string;
  description: string;
  status: 'draft' | 'active' | 'completed';
  createdAt: number;
  updatedAt: number;
}

interface Task {
  id: string;
  planId: string;
  title: string;
  description: string;
  status: 'pending' | 'in_progress' | 'done';
  priority: 'low' | 'medium' | 'high';
  dueDate?: number;
}

export default function Planner() {
  const navigate = useNavigate();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [activePlan, setActivePlan] = useState<Plan | null>(null);
  const [newPlanTitle, setNewPlanTitle] = useState('');
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  // Check authentication
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const session = await supabaseAuth.getSession();
        if (!session?.user) {
          navigate('/');
          return;
        }
        setIsLoading(false);
        // Load plans from localStorage for now (can be migrated to Supabase later)
        const savedPlans = localStorage.getItem('gos_plans');
        const savedTasks = localStorage.getItem('gos_tasks');
        if (savedPlans) setPlans(JSON.parse(savedPlans));
        if (savedTasks) setTasks(JSON.parse(savedTasks));
      } catch (error) {
        console.error('Auth check failed:', error);
        navigate('/');
      }
    };
    checkAuth();
  }, [navigate]);

  // Save to localStorage when plans/tasks change
  useEffect(() => {
    if (!isLoading) {
      localStorage.setItem('gos_plans', JSON.stringify(plans));
      localStorage.setItem('gos_tasks', JSON.stringify(tasks));
    }
  }, [plans, tasks, isLoading]);

  const createPlan = () => {
    if (!newPlanTitle.trim()) return;
    
    const plan: Plan = {
      id: crypto.randomUUID(),
      title: newPlanTitle,
      description: '',
      status: 'draft',
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    
    setPlans(prev => [...prev, plan]);
    setNewPlanTitle('');
    setActivePlan(plan);
  };

  const createTask = () => {
    if (!newTaskTitle.trim() || !activePlan) return;
    
    const task: Task = {
      id: crypto.randomUUID(),
      planId: activePlan.id,
      title: newTaskTitle,
      description: '',
      status: 'pending',
      priority: 'medium'
    };
    
    setTasks(prev => [...prev, task]);
    setNewTaskTitle('');
  };

  const updateTaskStatus = (taskId: string, status: Task['status']) => {
    setTasks(prev => prev.map(t => 
      t.id === taskId ? { ...t, status } : t
    ));
  };

  const deletePlan = (planId: string) => {
    if (confirm('Delete this plan and all its tasks?')) {
      setPlans(prev => prev.filter(p => p.id !== planId));
      setTasks(prev => prev.filter(t => t.planId !== planId));
      if (activePlan?.id === planId) setActivePlan(null);
    }
  };

  const deleteTask = (taskId: string) => {
    setTasks(prev => prev.filter(t => t.id !== taskId));
  };

  const activePlanTasks = tasks.filter(t => t.planId === activePlan?.id);

  const styles = {
    container: {
      display: 'flex',
      height: '100vh',
      background: '#0a0a0a',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace"
    },
    sidebar: {
      width: '280px',
      borderRight: '1px solid #1a1a1a',
      display: 'flex',
      flexDirection: 'column' as const,
      background: '#0d0d0d'
    },
    header: {
      padding: '16px',
      borderBottom: '1px solid #1a1a1a'
    },
    brand: {
      fontSize: '18px',
      fontWeight: 'bold',
      letterSpacing: '2px',
      color: '#4a9eff'
    },
    planList: {
      flex: 1,
      overflow: 'auto',
      padding: '8px 0'
    },
    planItem: {
      padding: '12px 16px',
      cursor: 'pointer',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      borderBottom: '1px solid #1a1a1a'
    },
    planItemActive: {
      background: '#1a1a1a'
    },
    main: {
      flex: 1,
      display: 'flex',
      flexDirection: 'column' as const
    },
    mainHeader: {
      padding: '16px 20px',
      borderBottom: '1px solid #1a1a1a',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between'
    },
    taskList: {
      flex: 1,
      overflow: 'auto',
      padding: '16px 20px'
    },
    taskItem: {
      padding: '12px 16px',
      background: '#1a1a1a',
      borderRadius: '4px',
      marginBottom: '8px',
      display: 'flex',
      alignItems: 'center',
      gap: '12px'
    },
    input: {
      background: '#1a1a1a',
      border: '1px solid #333',
      padding: '10px 14px',
      color: '#e0e0e0',
      fontFamily: "'Courier New', monospace",
      fontSize: '14px',
      borderRadius: '4px',
      flex: 1
    },
    button: {
      background: '#1a1a1a',
      border: '1px solid #333',
      padding: '10px 16px',
      color: '#4a9eff',
      fontFamily: "'Courier New', monospace",
      cursor: 'pointer',
      borderRadius: '4px'
    },
    statusBadge: {
      padding: '4px 8px',
      borderRadius: '4px',
      fontSize: '10px',
      fontWeight: 'bold',
      textTransform: 'uppercase' as const
    },
    deleteBtn: {
      background: 'transparent',
      border: 'none',
      color: '#f87171',
      cursor: 'pointer',
      padding: '4px 8px'
    },
    emptyState: {
      flex: 1,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: '#555'
    }
  };

  const getStatusColor = (status: Task['status']) => {
    switch (status) {
      case 'pending': return '#f59e0b';
      case 'in_progress': return '#4a9eff';
      case 'done': return '#4ade80';
      default: return '#666';
    }
  };

  if (isLoading) {
    return (
      <div style={{
        ...styles.container,
        alignItems: 'center',
        justifyContent: 'center'
      }}>
        <div>Loading Planner...</div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      {/* Sidebar - Plans list */}
      <div style={styles.sidebar}>
        <div style={styles.header}>
          <div style={styles.brand}>PLANNER</div>
          <div style={{ fontSize: '10px', color: '#666', marginTop: '4px' }}>
            smith net • v1.0
          </div>
        </div>

        {/* New plan input */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #1a1a1a' }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              style={{ ...styles.input, fontSize: '12px', padding: '8px 10px' }}
              placeholder="New plan name..."
              value={newPlanTitle}
              onChange={(e) => setNewPlanTitle(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && createPlan()}
            />
            <button
              style={{ ...styles.button, padding: '8px 12px' }}
              onClick={createPlan}
            >
              +
            </button>
          </div>
        </div>

        {/* Plans list */}
        <div style={styles.planList}>
          {plans.length === 0 ? (
            <div style={{ padding: '16px', color: '#555', textAlign: 'center' }}>
              No plans yet
            </div>
          ) : (
            plans.map(plan => (
              <div
                key={plan.id}
                style={{
                  ...styles.planItem,
                  ...(activePlan?.id === plan.id ? styles.planItemActive : {})
                }}
                onClick={() => setActivePlan(plan)}
              >
                <div>
                  <div style={{ fontWeight: 'bold' }}>{plan.title}</div>
                  <div style={{ fontSize: '10px', color: '#666', marginTop: '4px' }}>
                    {tasks.filter(t => t.planId === plan.id).length} tasks
                  </div>
                </div>
                <button
                  style={styles.deleteBtn}
                  onClick={(e) => {
                    e.stopPropagation();
                    deletePlan(plan.id);
                  }}
                >
                  ×
                </button>
              </div>
            ))
          )}
        </div>

        {/* Back to portal */}
        <div style={{ padding: '12px 16px', borderTop: '1px solid #1a1a1a' }}>
          <button
            style={{ ...styles.button, width: '100%', color: '#888' }}
            onClick={() => navigate('/portal')}
          >
            ← BACK TO PORTAL
          </button>
        </div>
      </div>

      {/* Main content - Tasks */}
      <div style={styles.main}>
        {activePlan ? (
          <>
            <div style={styles.mainHeader}>
              <div>
                <h2 style={{ margin: 0, fontSize: '18px' }}>{activePlan.title}</h2>
                <div style={{ fontSize: '12px', color: '#666', marginTop: '4px' }}>
                  Created {new Date(activePlan.createdAt).toLocaleDateString()}
                </div>
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                <input
                  style={styles.input}
                  placeholder="Add a task..."
                  value={newTaskTitle}
                  onChange={(e) => setNewTaskTitle(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && createTask()}
                />
                <button style={styles.button} onClick={createTask}>
                  ADD TASK
                </button>
              </div>
            </div>

            <div style={styles.taskList}>
              {activePlanTasks.length === 0 ? (
                <div style={styles.emptyState}>
                  No tasks yet. Add one above.
                </div>
              ) : (
                activePlanTasks.map(task => (
                  <div key={task.id} style={styles.taskItem}>
                    {/* Status selector */}
                    <select
                      value={task.status}
                      onChange={(e) => updateTaskStatus(task.id, e.target.value as Task['status'])}
                      style={{
                        background: '#0d0d0d',
                        border: '1px solid #333',
                        color: getStatusColor(task.status),
                        padding: '4px 8px',
                        borderRadius: '4px',
                        fontFamily: "'Courier New', monospace",
                        fontSize: '10px'
                      }}
                    >
                      <option value="pending">PENDING</option>
                      <option value="in_progress">IN PROGRESS</option>
                      <option value="done">DONE</option>
                    </select>

                    {/* Task title */}
                    <div style={{
                      flex: 1,
                      textDecoration: task.status === 'done' ? 'line-through' : 'none',
                      color: task.status === 'done' ? '#666' : '#e0e0e0'
                    }}>
                      {task.title}
                    </div>

                    {/* Delete button */}
                    <button
                      style={styles.deleteBtn}
                      onClick={() => deleteTask(task.id)}
                    >
                      ×
                    </button>
                  </div>
                ))
              )}
            </div>
          </>
        ) : (
          <div style={styles.emptyState}>
            Select a plan or create a new one
          </div>
        )}
      </div>
    </div>
  );
}
