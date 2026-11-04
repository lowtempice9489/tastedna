// 화면과 테스트가 공유하는 선택 규칙. 최종 검증은 서버에서 다시 수행한다.
export function activeGroups(question, selections = {}) {
  const active = new Set();
  return (question.comparisons || []).filter(group => {
    const c = group.showWhen;
    const enabled = !c || (active.has(c.comparisonId) && c.optionIds.includes(selections[c.comparisonId]));
    if (enabled) active.add(group.id);
    return enabled;
  });
}
export function selectionMap(answer) {
  return Object.fromEntries((answer?.selections || []).map(s => [s.comparisonId, s.selectedOptionIds ?? s.selectedOptionId]));
}
export const isRanked = group => group.selectionMode === 'RANKED_MULTI_SELECT';
function comparisonAnswer(question, selected) {
  return {selections: activeGroups(question, selected).filter(g => selected[g.id] !== undefined).map(g =>
    isRanked(g) ? {comparisonId:g.id, selectedOptionIds:selected[g.id]} : {comparisonId:g.id, selectedOptionId:selected[g.id]})};
}
export function choose(question, answer, optionId, groupId) {
  if (question.kind === 'COMPARISON') {
    const selected = {...selectionMap(answer)};
    const group = question.comparisons.find(g => g.id === groupId);
    if (isRanked(group)) {
      const ids = selected[groupId] || [];
      selected[groupId] = optionId === null && group.minSelections === 0 ? []
        : ids.includes(optionId) ? ids.filter(id => id !== optionId) : [...ids, optionId];
    } else selected[groupId] = optionId;
    return comparisonAnswer(question, selected);
  }
  if (question.kind === 'SINGLE_CHOICE') return {selectedOptionId: optionId};
  const exclusive = question.exclusiveOptionIds || [];
  let ids = [...(answer?.selectedOptionIds || [])];
  if (ids.includes(optionId)) ids = ids.filter(id => id !== optionId);
  else if (exclusive.includes(optionId)) ids = [optionId];
  else {
    ids = ids.filter(id => !exclusive.includes(id));
    if (ids.length >= question.maxSelections) return answer;
    ids.push(optionId);
  }
  return {selectedOptionIds:ids};
}
export function valid(question, answer) {
  if (!answer) return false;
  if (question.kind === 'COMPARISON') {
    const selections = selectionMap(answer), groups = activeGroups(question, selections);
    return Object.keys(answer).length === 1 && answer.selections?.length === groups.length
      && Object.keys(selections).length === groups.length
      && groups.every(g => {
        const entry = answer.selections.find(s => s.comparisonId === g.id);
        if (!entry || Object.keys(entry).length !== 2) return false;
        if (isRanked(g)) {
          const ids = entry.selectedOptionIds;
          return Number.isInteger(g.minSelections) && g.minSelections >= 0 && g.minSelections <= g.options.length
            && Array.isArray(ids) && ids.length >= g.minSelections && new Set(ids).size === ids.length
            && ids.every(id => g.options.some(o => o.id === id));
        }
        return (g.selectionMode === undefined || g.selectionMode === 'SINGLE')
          && typeof entry.selectedOptionId === 'string' && g.options.some(o => o.id === entry.selectedOptionId);
      });
  }
  const ids = question.kind === 'SINGLE_CHOICE' ? [answer.selectedOptionId] : answer.selectedOptionIds;
  return Array.isArray(ids) && ids.length > 0 && new Set(ids).size === ids.length
    && ids.length <= (question.maxSelections || 1)
    && ids.every(id => question.options.some(o => o.id === id))
    && (ids.length === 1 || !ids.some(id => (question.exclusiveOptionIds || []).includes(id)));
}

