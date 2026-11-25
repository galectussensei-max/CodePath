import axiosInstance, { unwrap } from './axiosInstance.js';

/** GET /api/learning/recommendations -> { recommendations[], lockedTopics[], note } */
export async function getRecommendations(limit = 5) {
  const response = await axiosInstance.get('/api/learning/recommendations', { params: { limit } });
  return unwrap(response);
}

/** GET /api/revision/queue -> { dueCount, items[], note } */
export async function getRevisionQueue() {
  const response = await axiosInstance.get('/api/revision/queue');
  return unwrap(response);
}

/** GET /api/analytics/summary -> { overview, topicMastery[], difficultyBreakdown[], verdictDistribution[], recurringErrors[], note } */
export async function getAnalyticsSummary() {
  const response = await axiosInstance.get('/api/analytics/summary');
  return unwrap(response);
}
