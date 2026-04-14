package com.example.review.review;

import com.example.review.auth.CurrentUserPrincipal;
import com.example.review.auth.RoleGuard;
import com.example.review.notification.NotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewReportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewReportService.class);
    private static final Set<String> SUBMITTABLE_STATUSES = Set.of("ACCEPTED", "IN_REVIEW", "OVERDUE");
    private static final Set<String> CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> RECOMMENDATIONS = Set.of("ACCEPT", "REJECT", "MINOR_REVISION", "MAJOR_REVISION", "DESK_REJECT");

    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final ReviewRoundRepository reviewRoundRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final NotificationService notificationService;

    public ReviewReportService(
            ReviewAssignmentRepository reviewAssignmentRepository,
            ReviewRoundRepository reviewRoundRepository,
            ReviewReportRepository reviewReportRepository,
            NotificationService notificationService
    ) {
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.reviewRoundRepository = reviewRoundRepository;
        this.reviewReportRepository = reviewReportRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReviewReportResponse submit(CurrentUserPrincipal principal, long assignmentId, SubmitReviewReportRequest request) {
        RoleGuard.requireRole(principal, "REVIEWER");
        validateRequest(request);

        ReviewAssignmentRow assignment = reviewAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (assignment.reviewerId() != principal.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment does not belong to current reviewer");
        }
        if (!SUBMITTABLE_STATUSES.contains(assignment.taskStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment cannot accept a review report from its current state");
        }
        if (reviewReportRepository.findByAssignmentId(assignmentId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review report already submitted for this assignment");
        }

        long reviewId = reviewReportRepository.nextReviewId();
        Timestamp submittedAt = Timestamp.from(Instant.now());
        reviewReportRepository.insert(
                reviewId,
                assignment.assignmentId(),
                assignment.roundId(),
                assignment.manuscriptId(),
                assignment.reviewerId(),
                request,
                submittedAt
        );
        reviewAssignmentRepository.markSubmitted(assignment.assignmentId(), submittedAt);

        reviewRoundRepository.findById(assignment.roundId()).ifPresent(round -> {
            try {
                notificationService.notifyReviewSubmitted(round.createdBy(), assignment.manuscriptId(), assignment.assignmentId());
            } catch (Exception ex) {
                LOGGER.warn("Review-submitted notification failed for assignment {}", assignment.assignmentId(), ex);
            }
        });

        return new ReviewReportResponse(reviewId, assignment.assignmentId(), "SUBMITTED");
    }

    private void validateRequest(SubmitReviewReportRequest request) {
        validateScore(request.noveltyScore(), "noveltyScore");
        validateScore(request.methodScore(), "methodScore");
        validateScore(request.experimentScore(), "experimentScore");
        validateScore(request.writingScore(), "writingScore");
        validateScore(request.overallScore(), "overallScore");
        if (!CONFIDENCE_LEVELS.contains(request.confidenceLevel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confidence level");
        }
        if (!RECOMMENDATIONS.contains(request.recommendation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recommendation");
        }
    }

    private void validateScore(int score, String fieldName) {
        if (score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be between 1 and 5");
        }
    }

}

record SubmitReviewReportRequest(
        int noveltyScore,
        int methodScore,
        int experimentScore,
        int writingScore,
        int overallScore,
        String confidenceLevel,
        String strengths,
        String weaknesses,
        String commentsToAuthor,
        String commentsToChair,
        String recommendation
) {
}

record ReviewReportResponse(long reviewId, long assignmentId, String taskStatus) {
}
