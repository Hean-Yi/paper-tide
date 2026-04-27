package com.example.review.workflow;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DecisionWorkbenchQueryService {
    private final DecisionWorkbenchReadRepository repository;

    public DecisionWorkbenchQueryService(DecisionWorkbenchReadRepository repository) {
        this.repository = repository;
    }

    public List<DecisionWorkbenchItem> listDecisionWorkbench(CurrentUserPrincipal principal) {
        RoleGuard.requireChairOrAdmin(principal);

        List<DecisionWorkbenchBase> rounds = repository.findPendingAndInProgressRounds();
        if (rounds.isEmpty()) {
            return List.of();
        }

        List<Long> roundIds = rounds.stream().map(DecisionWorkbenchBase::roundId).toList();

        var assignmentMap = repository.findAssignmentsByRoundIds(roundIds);
        var intentMap = repository.findConflictIntentsByRoundIds(roundIds);
        var projectionMap = repository.findConflictProjectionsByRoundIds(roundIds);

        return rounds.stream()
                .map(round -> new DecisionWorkbenchItem(
                        round.roundId(),
                        round.manuscriptId(),
                        round.versionId(),
                        round.versionNo(),
                        round.roundNo(),
                        round.title(),
                        round.currentStatus(),
                        round.roundStatus(),
                        round.deadlineAt(),
                        round.assignmentCount(),
                        round.submittedReviewCount(),
                        round.conflictCount(),
                        round.lastDecisionCode(),
                        assignmentMap.getOrDefault(round.roundId(), List.of()),
                        intentMap.get(round.roundId()),
                        projectionMap.getOrDefault(round.roundId(), List.of())
                ))
                .toList();
    }
}
