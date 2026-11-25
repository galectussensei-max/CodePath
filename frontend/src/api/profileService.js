import axiosInstance, { unwrap } from './axiosInstance.js';

/**
 * @typedef {Object} Profile
 * @property {number} userId
 * @property {string} name
 * @property {string} email
 * @property {?string} programmingBackground
 * @property {?string} dsaExperience
 * @property {?string} prepGoal
 * @property {?string} targetLevel
 * @property {?number} dailyStudyMinutes
 * @property {boolean} diagnosticCompleted
 */

/** @returns {Promise<Profile>} */
export async function getProfile() {
  return unwrap(await axiosInstance.get('/api/users/profile'));
}

/**
 * PUT replaces all five onboarding fields. prepGoal and dailyStudyMinutes (10-720) are required.
 * @returns {Promise<Profile>}
 */
export async function updateProfile({
  programmingBackground,
  dsaExperience,
  prepGoal,
  targetLevel,
  dailyStudyMinutes,
}) {
  const response = await axiosInstance.put('/api/users/profile', {
    programmingBackground,
    dsaExperience,
    prepGoal,
    targetLevel,
    dailyStudyMinutes,
  });
  return unwrap(response);
}
