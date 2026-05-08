package com.example.review.conference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.review.auth.CurrentUserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConferenceServiceTest {
    private FakeConferenceRepository repository;
    private ConferenceService service;

    @BeforeEach
    void setUp() {
        repository = new FakeConferenceRepository();
        service = new ConferenceService(
                repository,
                Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void chairCreatesDraftConferenceWithFixedPhaseSchedule() {
        ConferenceDetail detail = service.createDraft(chairPrincipal(), validDraftRequest());

        assertEquals("DRAFT", detail.status());
        assertEquals(1003L, detail.organizerUserId());
        assertEquals("DOUBLE_BLIND", detail.blindMode());
        assertEquals("review-systems-2026", detail.publicSlug());
        assertEquals(List.of("NLP", "SE"), detail.topicAreas());
        assertEquals(3, detail.targetReviewsPerPaper());
        assertEquals(4, detail.defaultReviewerMaxLoad());
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), detail.phase().submissionOpenAt());
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), detail.phase().submissionCloseAt());
    }

    @Test
    void onlyChairOrAdminCanCreateConferenceDrafts() {
        assertThrows(ConferenceAccessException.class, () ->
                service.createDraft(authorPrincipal(), validDraftRequest())
        );
    }

    @Test
    void adminApprovalPublishesConferenceForPublicCfpReads() {
        ConferenceDetail draft = service.createDraft(chairPrincipal(), validDraftRequest());
        ConferenceDetail pending = service.submitForApproval(draft.conferenceId(), chairPrincipal());

        assertEquals("PENDING_APPROVAL", pending.status());

        ConferenceDetail approved = service.approveConference(draft.conferenceId(), adminPrincipal());
        assertEquals("OPEN_FOR_SUBMISSION", approved.status());
        assertTrue(approved.cfpPublished());
        assertEquals(1004L, approved.approvedBy());

        List<ConferenceSummary> publicList = service.listPublicCfps();
        assertEquals(1, publicList.size());
        assertEquals("Review Systems 2026", publicList.getFirst().name());

        ConferenceDetail publicDetail = service.getPublicCfp("review-systems-2026");
        assertEquals("Call for papers", publicDetail.cfpText());
    }

    @Test
    void publicCfpReadsExcludeDraftAndPendingConferences() {
        ConferenceDetail draft = service.createDraft(chairPrincipal(), validDraftRequest());
        service.submitForApproval(draft.conferenceId(), chairPrincipal());

        assertTrue(service.listPublicCfps().isEmpty());
        assertThrows(ConferenceNotFoundException.class, () -> service.getPublicCfp("review-systems-2026"));
    }

    @Test
    void lifecycleTransitionsAreOrderedAndIdempotent() {
        ConferenceDetail draft = service.createDraft(chairPrincipal(), validDraftRequest());
        service.submitForApproval(draft.conferenceId(), chairPrincipal());
        service.approveConference(draft.conferenceId(), adminPrincipal());

        ConferenceDetail closed = service.advanceStatus(draft.conferenceId(), chairPrincipal(), "SUBMISSION_CLOSED");
        assertEquals("SUBMISSION_CLOSED", closed.status());
        assertEquals("SUBMISSION_CLOSED", service.advanceStatus(draft.conferenceId(), chairPrincipal(), "SUBMISSION_CLOSED").status());

        assertThrows(ConferenceValidationException.class, () ->
                service.advanceStatus(draft.conferenceId(), chairPrincipal(), "REVIEWING")
        );
    }

    @Test
    void chairsCanListAndOpenOnlyTheirManageableConferences() {
        ConferenceDetail owned = service.createDraft(chairPrincipal(), validDraftRequest());
        ConferenceDetail other = service.createDraft(
                new CurrentUserPrincipal(2003L, "other_chair", List.of("CHAIR")),
                new ConferenceDraftRequest(
                        "Other Conference",
                        "OC",
                        2026,
                        "DOUBLE_BLIND",
                        "Other call",
                        List.of("Systems"),
                        3,
                        4,
                        "other-conference-2026",
                        new ConferencePhaseRequest(
                                Instant.parse("2026-06-01T00:00:00Z"),
                                Instant.parse("2026-07-01T00:00:00Z"),
                                Instant.parse("2026-07-10T00:00:00Z"),
                                Instant.parse("2026-07-20T00:00:00Z"),
                                Instant.parse("2026-08-20T00:00:00Z"),
                                Instant.parse("2026-09-01T00:00:00Z")
                        )
                )
        );

        List<ConferenceSummary> chairConferences = service.listManageableConferences(chairPrincipal());

        assertEquals(1, chairConferences.size());
        assertEquals(owned.conferenceId(), chairConferences.getFirst().conferenceId());
        assertEquals(owned.conferenceId(), service.getManageableConference(owned.conferenceId(), chairPrincipal()).conferenceId());
        assertThrows(ConferenceAccessException.class, () ->
                service.getManageableConference(other.conferenceId(), chairPrincipal())
        );
    }

    @Test
    void adminsCanListEveryManageableConference() {
        service.createDraft(chairPrincipal(), validDraftRequest());
        service.createDraft(
                new CurrentUserPrincipal(2003L, "other_chair", List.of("CHAIR")),
                new ConferenceDraftRequest(
                        "Other Conference",
                        "OC",
                        2026,
                        "DOUBLE_BLIND",
                        "Other call",
                        List.of("Systems"),
                        3,
                        4,
                        "other-conference-2026",
                        new ConferencePhaseRequest(
                                Instant.parse("2026-06-01T00:00:00Z"),
                                Instant.parse("2026-07-01T00:00:00Z"),
                                Instant.parse("2026-07-10T00:00:00Z"),
                                Instant.parse("2026-07-20T00:00:00Z"),
                                Instant.parse("2026-08-20T00:00:00Z"),
                                Instant.parse("2026-09-01T00:00:00Z")
                        )
                )
        );

        assertEquals(2, service.listManageableConferences(adminPrincipal()).size());
    }

    private ConferenceDraftRequest validDraftRequest() {
        return new ConferenceDraftRequest(
                "Review Systems 2026",
                "RS",
                2026,
                "DOUBLE_BLIND",
                "Call for papers",
                List.of("NLP", "SE"),
                3,
                4,
                "review-systems-2026",
                new ConferencePhaseRequest(
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-10T00:00:00Z"),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-08-20T00:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z")
                )
        );
    }

    private CurrentUserPrincipal chairPrincipal() {
        return new CurrentUserPrincipal(1003L, "chair_demo", List.of("CHAIR"));
    }

    private CurrentUserPrincipal adminPrincipal() {
        return new CurrentUserPrincipal(1004L, "admin_demo", List.of("ADMIN"));
    }

    private CurrentUserPrincipal authorPrincipal() {
        return new CurrentUserPrincipal(1001L, "author_demo", List.of("AUTHOR"));
    }

    private static final class FakeConferenceRepository implements ConferenceRepository {
        private long nextConferenceId = 1;
        private long nextPhaseId = 1;
        private final Map<Long, StoredConference> conferences = new HashMap<>();
        private final Map<Long, ConferencePhase> phases = new HashMap<>();

        @Override
        public long createConference(ConferenceDraft draft) {
            long id = nextConferenceId++;
            conferences.put(id, new StoredConference(
                    id,
                    draft.name(),
                    draft.acronym(),
                    draft.year(),
                    draft.organizerUserId(),
                    draft.status(),
                    draft.blindMode(),
                    draft.cfpText(),
                    draft.topicAreas(),
                    draft.targetReviewsPerPaper(),
                    draft.defaultReviewerMaxLoad(),
                    draft.publicSlug(),
                    draft.cfpPublished(),
                    null,
                    null
            ));
            return id;
        }

        @Override
        public void createPhase(long conferenceId, ConferencePhaseDraft draft) {
            phases.put(conferenceId, new ConferencePhase(
                    nextPhaseId++,
                    conferenceId,
                    draft.submissionOpenAt(),
                    draft.submissionCloseAt(),
                    draft.biddingOpenAt(),
                    draft.biddingCloseAt(),
                    draft.reviewDeadlineAt(),
                    draft.decisionReleaseAt()
            ));
        }

        @Override
        public Optional<ConferenceDetail> findDetail(long conferenceId) {
            StoredConference conference = conferences.get(conferenceId);
            if (conference == null) {
                return Optional.empty();
            }
            return Optional.of(toDetail(conference));
        }

        @Override
        public Optional<ConferenceDetail> findPublicCfpBySlug(String publicSlug) {
            return conferences.values().stream()
                    .filter(conference -> conference.publicSlug().equals(publicSlug))
                    .filter(conference -> conference.cfpPublished()
                            && List.of("OPEN_FOR_SUBMISSION", "SUBMISSION_CLOSED", "BIDDING_OPEN",
                            "REVIEW_ASSIGNMENT", "REVIEWING", "DECISION", "CLOSED").contains(conference.status()))
                    .findFirst()
                    .map(this::toDetail);
        }

        @Override
        public List<ConferenceSummary> listPublicCfps() {
            return conferences.values().stream()
                    .filter(conference -> conference.cfpPublished()
                            && List.of("OPEN_FOR_SUBMISSION", "SUBMISSION_CLOSED", "BIDDING_OPEN",
                            "REVIEW_ASSIGNMENT", "REVIEWING", "DECISION", "CLOSED").contains(conference.status()))
                    .map(conference -> new ConferenceSummary(
                            conference.conferenceId(),
                            conference.name(),
                            conference.acronym(),
                            conference.year(),
                            conference.status(),
                            conference.blindMode(),
                            conference.publicSlug(),
                            phases.get(conference.conferenceId()).submissionOpenAt(),
                            phases.get(conference.conferenceId()).submissionCloseAt()
                    ))
                    .toList();
        }

        @Override
        public List<ConferenceSummary> listPendingApproval() {
            return conferences.values().stream()
                    .filter(conference -> conference.status().equals("PENDING_APPROVAL"))
                    .map(conference -> new ConferenceSummary(
                            conference.conferenceId(),
                            conference.name(),
                            conference.acronym(),
                            conference.year(),
                            conference.status(),
                            conference.blindMode(),
                            conference.publicSlug(),
                            phases.get(conference.conferenceId()).submissionOpenAt(),
                            phases.get(conference.conferenceId()).submissionCloseAt()
                    ))
                    .toList();
        }

        @Override
        public List<ConferenceSummary> listManageable(Long organizerUserId) {
            return conferences.values().stream()
                    .filter(conference -> organizerUserId == null || conference.organizerUserId() == organizerUserId)
                    .map(conference -> new ConferenceSummary(
                            conference.conferenceId(),
                            conference.name(),
                            conference.acronym(),
                            conference.year(),
                            conference.status(),
                            conference.blindMode(),
                            conference.publicSlug(),
                            phases.get(conference.conferenceId()).submissionOpenAt(),
                            phases.get(conference.conferenceId()).submissionCloseAt()
                    ))
                    .toList();
        }

        @Override
        public boolean publicSlugExists(String publicSlug) {
            return conferences.values().stream().anyMatch(conference -> conference.publicSlug().equals(publicSlug));
        }

        @Override
        public void updateStatus(long conferenceId, String status, boolean cfpPublished, Long approvedBy, Instant approvedAt) {
            StoredConference conference = conferences.get(conferenceId);
            conferences.put(conferenceId, conference.withStatus(status, cfpPublished, approvedBy, approvedAt));
        }

        private ConferenceDetail toDetail(StoredConference conference) {
            return new ConferenceDetail(
                    conference.conferenceId(),
                    conference.name(),
                    conference.acronym(),
                    conference.year(),
                    conference.organizerUserId(),
                    conference.status(),
                    conference.blindMode(),
                    conference.cfpText(),
                    conference.topicAreas(),
                    conference.targetReviewsPerPaper(),
                    conference.defaultReviewerMaxLoad(),
                    conference.publicSlug(),
                    conference.cfpPublished(),
                    conference.approvedBy(),
                    conference.approvedAt(),
                    phases.get(conference.conferenceId())
            );
        }

        private record StoredConference(
                long conferenceId,
                String name,
                String acronym,
                int year,
                long organizerUserId,
                String status,
                String blindMode,
                String cfpText,
                List<String> topicAreas,
                int targetReviewsPerPaper,
                int defaultReviewerMaxLoad,
                String publicSlug,
                boolean cfpPublished,
                Long approvedBy,
                Instant approvedAt
        ) {
            StoredConference withStatus(String nextStatus, boolean nextPublished, Long nextApprovedBy, Instant nextApprovedAt) {
                return new StoredConference(conferenceId, name, acronym, year, organizerUserId, nextStatus,
                        blindMode, cfpText, new ArrayList<>(topicAreas), targetReviewsPerPaper,
                        defaultReviewerMaxLoad, publicSlug, nextPublished, nextApprovedBy, nextApprovedAt);
            }
        }
    }
}
