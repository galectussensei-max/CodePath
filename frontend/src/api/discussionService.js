import axiosInstance, { unwrap } from './axiosInstance.js';

export async function listDiscussions(questionId) {
  return unwrap(await axiosInstance.get(`/api/discussions/question/${questionId}`));
}

export async function createDiscussion(questionId, { title, content }) {
  return unwrap(await axiosInstance.post(`/api/discussions/question/${questionId}`, { title, content }));
}

export async function getDiscussion(discussionId) {
  return unwrap(await axiosInstance.get(`/api/discussions/${discussionId}`));
}

export async function addComment(discussionId, content) {
  return unwrap(await axiosInstance.post(`/api/discussions/${discussionId}/comments`, { content }));
}

/** An author upvoting their own thread gets a 409. */
export async function upvoteDiscussion(discussionId) {
  return unwrap(await axiosInstance.post(`/api/discussions/${discussionId}/upvote`));
}
