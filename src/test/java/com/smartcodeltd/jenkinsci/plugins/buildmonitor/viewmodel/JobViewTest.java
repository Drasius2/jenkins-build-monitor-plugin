package com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel;

import com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.syntacticsugar.BuildStateRecipe;
import com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.syntacticsugar.JobStateRecipe;
import hudson.model.Job;
import hudson.model.Result;
import org.junit.Ignore;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.syntacticsugar.Loops.asFollows;
import static com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.syntacticsugar.TimeMachine.assumeThat;
import static com.smartcodeltd.jenkinsci.plugins.buildmonitor.viewmodel.syntacticsugar.TimeMachine.assumeThatCurrentTime;
import static hudson.model.Result.*;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jan Molak
 */
public class JobViewTest {

    private static final String theName     = "some-TLAs-followed-by-a-project-name";
    private static final String displayName = "Pretty name that has some actual meaning";
    private JobView view;

    @Test
    public void should_know_the_name_of_the_job_its_based_on() {
        view = JobView.of(a(job().withName(theName)));

        assertThat(view.name(), is(theName));
        assertThat(view.toString(), is(theName));
    }

    /*
     * If you were not aware of this, the configuration page of each job has an "Advanced Project Options"
     * section, where you can set a user-friendly "Display Name"
     */
    @Test
    public void should_prefer_the_display_name_over_actual_name() {
        view = JobView.of(a(job().withName(theName).withDisplayName(displayName)));

        assertThat(view.name(), is(displayName));
        assertThat(view.toString(), is(displayName));
    }

    @Test
    public void should_know_the_url_of_the_job() {
        view = JobView.of(a(job().withName(theName).withDisplayName(displayName)));

        assertThat(view.url(), is("job/" + theName));
    }

    @Test
    public void should_know_current_build_number() {
        view = JobView.of(a(job().whereTheLast(build().numberIs(5))));

        assertThat(view.lastBuildName(), is("#5"));
    }

    @Test
    public void should_use_build_name_if_its_known() {
        view = JobView.of(a(job().whereTheLast(build().nameIs("1.3.4+build.15"))));

        assertThat(view.lastBuildName(), is("1.3.4+build.15"));
    }

    @Test
    public void should_admit_if_it_doesnt_know_either_build_number_nor_build_name() {
        view = JobView.of(a(job().thatHasNeverRun()));

        assertThat(view.lastBuildName(), is(nullValue()));
    }

    @Test
    public void should_know_the_url_of_the_build() {
        // setting url on the stub is far from ideal, but hudson.model.Run is not particularly easy to test ...
        view = JobView.of(a(job().whereTheLast(build().urlIs("job/project-name/22/"))));

        assertThat(view.lastBuildUrl(), is("job/project-name/22/"));
    }

    /*
     * Should be able to measure the progress
     */

    @Test
    public void progress_of_a_not_started_job_should_be_zero() {
        view = JobView.of(a(job()));

        assertThat(view.progress(), is(0));
    }

    @Test
    public void progress_of_a_finished_job_should_be_zero() {
        view = JobView.of(a(job().whereTheLast(build().finishedWith(SUCCESS))));

        assertThat(view.progress(), is(0));
    }

    @Test
    public void progress_of_a_nearly_finished_job_should_be_100() throws Exception {
        view = JobView.of(
                    a(job().whereTheLast(build().isStillBuilding().startedAt("12:00:00").andUsuallyTakes(0))),
                    assumingThatCurrentTimeIs("12:00:00")
        );

        assertThat(view.progress(), is(100));
    }

    @Test
    public void progress_of_a_job_thats_taking_longer_than_expected_should_be_100() throws Exception {
        view = JobView.of(
                a(job().whereTheLast(build().isStillBuilding().startedAt("12:00:00").andUsuallyTakes(5))),
                assumingThatCurrentTimeIs("12:20:00")
        );

        assertThat(view.progress(), is(100));
    }

    @Test
    public void should_calculate_the_progress_of_a_running_job() throws Exception {
        view = JobView.of(
                a(job().whereTheLast(build().isStillBuilding().startedAt("13:10:00").andUsuallyTakes(5))),
                assumingThatCurrentTimeIs("13:11:00")
        );

        assertThat(view.progress(), is(20));
    }

    /*
     * Elapsed time
     */

    @Test
    public void should_know_how_long_a_build_has_been_running_for() throws Exception {

        String startTime              = "13:10:00",
               sixSecondsLater        = "13:10:06",
               twoAndHalfMinutesLater = "13:12:30",
               anHourAndHalfLater     = "14:40:00";
        Date   currentTime = assumeThatCurrentTime().is(startTime);

        view = JobView.of(
                a(job().whereTheLast(build().startedAt(startTime).isStillBuilding())),
                currentTime
        );

        assumeThat(currentTime).is(sixSecondsLater);
        assertThat(view.lastBuildDuration(), is("6s"));

        assumeThat(currentTime).is(twoAndHalfMinutesLater);
        assertThat(view.lastBuildDuration(), is("2m 30s"));

        assumeThat(currentTime).is(anHourAndHalfLater);
        assertThat(view.lastBuildDuration(), is("1h 30m 0s"));
    }

    @Test
    public void should_know_how_long_the_last_build_took_once_its_finished() throws Exception {
        view = JobView.of(a(job().whereTheLast(build().finishedWith(SUCCESS).andTook(3))));

        assertThat(view.lastBuildDuration(), is("3m 0s"));
    }

    @Test
    public void should_not_say_anything_about_the_duration_if_the_build_hasnt_run_yet() throws Exception {
        view = JobView.of(a(job()));

        assertThat(view.lastBuildDuration(), is(""));
    }

    @Test
    public void should_know_how_long_the_next_build_is_supposed_to_take() throws Exception {
        view = JobView.of(a(job().whereTheLast(build().finishedWith(SUCCESS).andUsuallyTakes(5))));

        assertThat(view.estimatedDurationOfNextBuild(), is("5m 0s"));
    }

    @Test
    public void should_not_say_anything_if_it_doesnt_know_how_long_the_next_build_is_supposed_to_take() throws Exception {
        view = JobView.of(a(job()));

        assertThat(view.estimatedDurationOfNextBuild(), is(""));
    }
    
    /*
     * Should produce a meaningful status description that can be used in the CSS
     */

    @Test
    public void should_describe_the_job_as_successful_if_the_last_build_succeeded() {
        view = JobView.of(a(job().whereTheLast(build().finishedWith(SUCCESS))));

        assertThat(view.status(), containsString("successful"));
    }

    @Test
    public void should_describe_the_job_as_failing_if_the_last_build_failed() {
        for (Result result : asFollows(FAILURE, ABORTED, NOT_BUILT, UNSTABLE)) {
            view = JobView.of(a(job().whereTheLast(build().finishedWith(result))));

            assertThat(view.status(), containsString("failing"));
        }
    }

    @Test
    public void should_describe_the_job_as_running_if_it_is_running() {
        List<JobView> views = asFollows(
                JobView.of(a(job().whereTheLast(build().hasntStartedYet()))),
                JobView.of(a(job().whereTheLast(build().isStillBuilding()))),
                JobView.of(a(job().whereTheLast(build().isStillUpdatingTheLog())))
        );

        for (JobView view : views) {
            assertThat(view.status(), containsString("running"));
        }
    }

    @Test
    public void should_describe_the_job_as_running_and_successful_if_it_is_running_and_the_previous_build_succeeded() {
        List<JobView> views = asFollows(
                JobView.of(a(job().
                        whereTheLast(build().hasntStartedYet()).
                        andThePrevious(build().finishedWith(SUCCESS)))),

                JobView.of(a(job().
                        whereTheLast(build().isStillBuilding()).
                        andThePrevious(build().finishedWith(SUCCESS)))),

                JobView.of(a(job().
                        whereTheLast(build().isStillUpdatingTheLog()).
                        andThePrevious(build().finishedWith(SUCCESS))))
        );


        // I could do this instead of having two assertions:
        // assertThat(view.status(), both(containsString("successful")).and(containsString("running")));
        // but then it would require Java 7

        for (JobView view : views) {
            assertThat(view.status(), containsString("successful"));
            assertThat(view.status(), containsString("running"));
        }
    }

    @Test
    public void should_describe_the_job_as_running_and_failing_if_it_is_running_and_the_previous_build_failed() {
        List<JobView> views = asFollows(
                JobView.of(a(job().
                        whereTheLast(build().hasntStartedYet()).
                        andThePrevious(build().finishedWith(FAILURE)))),

                JobView.of(a(job().
                        whereTheLast(build().isStillBuilding()).
                        andThePrevious(build().finishedWith(FAILURE)))),

                JobView.of(a(job().
                        whereTheLast(build().isStillUpdatingTheLog()).
                        andThePrevious(build().finishedWith(FAILURE))))
        );

        for (JobView view : views) {
            assertThat(view.status(), containsString("failing"));
            assertThat(view.status(), containsString("running"));
        }
    }

    /*
     * Parallel build execution handling
     */

    @Test
    public void should_describe_the_job_as_successful_when_there_are_several_builds_running_in_parallel_and_the_last_completed_was_successful() {
        view = JobView.of(a(job().
                        whereTheLast(build().isStillBuilding()).
                        andThePrevious(build().isStillBuilding()).
                        andThePrevious(build().finishedWith(SUCCESS))));

        assertThat(view.status(), containsString("successful"));
    }

    @Test
    public void should_describe_the_job_as_failing_when_there_are_several_builds_running_in_parallel_and_the_last_completed_failed() {
        view = JobView.of(a(job().
                whereTheLast(build().isStillBuilding()).
                andThePrevious(build().isStillBuilding()).
                andThePrevious(build().finishedWith(FAILURE))));

        assertThat(view.status(), containsString("failing"));
    }
    
    /*
     * Should produce some basic build statistics
     */

    @Test
    @Ignore
    public void should_know_how_long_the_job_has_been_failing_for() {
        // TODO Implement missing feature
    }

    /*
     * Should know who broke the build
     */

    @Test
    public void should_know_who_broke_the_build() {
        view = JobView.of(a(job().whereTheLast(build().wasBrokenBy("Adam", "Ben"))));

        assertThat(view.culprits(), hasSize(2));
        assertThat(view.culprits(), hasItems("Adam", "Ben"));
    }

    @Test
    public void should_know_who_has_been_committing_over_broken_build() {
        view = JobView.of(a(job().
                whereTheLast(build().wasBrokenBy("Adam")).
                andThePrevious(build().wasBrokenBy("Ben", "Connor")).
                andThePrevious(build().wasBrokenBy("Daniel")).
                andThePrevious(build().succeededThanksTo("Errol"))));

        assertThat(view.culprits(), hasSize(4));
        assertThat(view.culprits(), hasItems("Adam", "Ben", "Connor", "Daniel"));
        assertThat(view.culprits(), not(hasItem("Errol")));
    }

    @Test
    public void should_only_mention_each_culprit_once() {
        view = JobView.of(a(job().
                whereTheLast(build().wasBrokenBy("Adam")).
                andThePrevious(build().wasBrokenBy("Adam", "Ben")).
                andThePrevious(build().wasBrokenBy("Ben", "Connor"))));

        assertThat(view.culprits(), hasSize(3));
        assertThat(view.culprits(), hasItems("Adam", "Ben", "Connor"));
    }

    @Test
    public void should_not_mention_any_culprits_if_the_build_was_successful() {
        view = JobView.of(a(job().whereTheLast(build().succeededThanksTo("Adam"))));

        assertThat(view.culprits(), hasSize(0));
    }

    @Test
    public void should_not_mention_any_culprits_if_the_build_was_successful_and_is_still_running() {
        view = JobView.of(a(job().
                whereTheLast(build().isStillBuilding()).
                andThePrevious(build().succeededThanksTo("Adam"))));

        assertThat(view.culprits(), hasSize(0));
    }

    @Test
    @Ignore
    public void should_know_the_authors_of_commits_that_made_it_into_the_build() {
        //TODO implement shouldKnowTheAuthorsOfCommitsThatMadeItIntoTheBuild
//        List<JobView> views = asFollows(
//            JobView.of(a(job().whereTheLast(build().succeededThanksTo("Adam")))),
//            JobView.of(a(job().whereTheLast(build().wasBrokenBy("Adam"))))
//        );
//
//        for (JobView view : views) {
//            assertThat(view.authors(), hasSize(1));
//            assertThat(view.authors(), hasItems("Adam"));
//        }
    }

    /*
     * Syntactic sugar
     */

    private JobStateRecipe job() {
        return new JobStateRecipe();
    }

    private Job<?, ?> a(JobStateRecipe recipe) {
        return recipe.execute();
    }

    private BuildStateRecipe build() {
        return new BuildStateRecipe();
    }

    private Date assumingThatCurrentTimeIs(String currentTime) throws ParseException {
        Date currentDate = new SimpleDateFormat("H:m:s").parse(currentTime);

        Date systemTime = mock(Date.class);
        when(systemTime.getTime()).thenReturn(currentDate.getTime());

        return systemTime;
    }
}