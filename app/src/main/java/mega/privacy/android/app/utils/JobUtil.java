package mega.privacy.android.app.utils;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.text.format.DateUtils;

import java.util.List;

import mega.privacy.android.app.jobservices.CameraUploadsService;
import mega.privacy.android.app.jobservices.DaemonService;

import static mega.privacy.android.app.utils.Util.logJobState;

@TargetApi(21)
public class JobUtil {
    //todo short time for testing purpose
    public static final long SCHEDULER_INTERVAL = 15 * DateUtils.MINUTE_IN_MILLIS;
    
    public static final long SCHEDULER_INTERVAL_ANDROID_5_6 = 1 * DateUtils.MINUTE_IN_MILLIS;

    public static final int START_JOB_FAILED = -1;

    public static final int PHOTOS_UPLOAD_JOB_ID = Constants.PHOTOS_UPLOAD_JOB_ID;

    public static final int DAEMON_JOB_ID = 9979;

    public static final long DAEMON_INTERVAL = DateUtils.HOUR_IN_MILLIS;

    public static synchronized boolean isJobScheduled(Context context,int id) {
        JobScheduler js = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            List<JobInfo> jobs = js.getAllPendingJobs();
            for (JobInfo info : jobs) {
                if (info.getId() == id) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int restart(Context context) {
        cancelAllJobs(context);
        return startJob(context);
    }

    public static synchronized int startDAEMON(Context context) {
        if (isJobScheduled(context,DAEMON_JOB_ID)) {
            return START_JOB_FAILED;
        }
        JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(DAEMON_JOB_ID,new ComponentName(context,DaemonService.class));
            jobInfoBuilder.setPeriodic(DAEMON_INTERVAL);
            jobInfoBuilder.setPersisted(true);
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            int result = jobScheduler.schedule(jobInfoBuilder.build());
            logJobState(result,DaemonService.class.getName());
            return result;
        }
        return START_JOB_FAILED;
    }

    public static synchronized int startJob(Context context) {
        if (isJobScheduled(context,PHOTOS_UPLOAD_JOB_ID)) {
            return START_JOB_FAILED;
        }
        JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(PHOTOS_UPLOAD_JOB_ID,new ComponentName(context,CameraUploadsService.class));
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
                jobInfoBuilder.setPeriodic(SCHEDULER_INTERVAL);
            }else{
                jobInfoBuilder.setPeriodic(SCHEDULER_INTERVAL_ANDROID_5_6);
            }
            jobInfoBuilder.setPersisted(true);

            int result = jobScheduler.schedule(jobInfoBuilder.build());
            logJobState(result,CameraUploadsService.class.getName());
            return result;
        }
        return START_JOB_FAILED;
    }


    public static synchronized void cancelAllJobs(Context context) {
        JobScheduler js = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            CameraUploadsService.IS_CANCELLED_BY_USER = true;
            js.cancelAll();
        }
    }
}
