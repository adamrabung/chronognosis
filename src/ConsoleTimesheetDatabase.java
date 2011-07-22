import java.io.IOException;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ConsoleTimesheetDatabase implements ITimesheetDatabase {
  private static final int TIME_WINDOW_SIZE_MINUTES = 1;

  private int _timeWindow;
  private final Set<String> _projectsAccessedInTimeWindow = new HashSet<String>();

  public ConsoleTimesheetDatabase() {
    _timeWindow = getCurrentTimeWindow();
  }

  private int getCurrentTimeWindow() {
    Calendar c = Calendar.getInstance();
    return ((c.get(Calendar.HOUR_OF_DAY) * 60) + c.get(Calendar.MINUTE)) / TIME_WINDOW_SIZE_MINUTES;
  }

  @Override
  public void onProjectFileAccessed(String projectName) {
    System.out.println("Project accessed " + projectName);
    int currTimeWindow = getCurrentTimeWindow();
    if (currTimeWindow == _timeWindow) {
      _projectsAccessedInTimeWindow.add(projectName);
    }
    else {
      Calendar windowStart = Calendar.getInstance();
      windowStart.set(Calendar.HOUR_OF_DAY, 0);
      windowStart.set(Calendar.MINUTE, 0);
      windowStart.set(Calendar.SECOND, 0);
      windowStart.set(Calendar.MILLISECOND, 0);
      windowStart.add(Calendar.MINUTE, _timeWindow * TIME_WINDOW_SIZE_MINUTES);

      System.out.println("Projects accessed in the " + TIME_WINDOW_SIZE_MINUTES + " minute period starting " + windowStart.getTime());
      for (String project : _projectsAccessedInTimeWindow) {
        System.out.println("\t" + project);
      }
      _projectsAccessedInTimeWindow.clear();
      _timeWindow = currTimeWindow;
    }
  }

  public static void main(String[] args) throws IOException {
    ITimesheetDatabase db = new ConsoleTimesheetDatabase();
    IPathWatcher proj1 = new TimesheetPathWatcher(db, "Project Epsilon", Paths.get("c:\\temp\\epsilon"), Paths.get("c:\\temp\\epsilon2"));
    IPathWatcher proj2 = new TimesheetPathWatcher(db, "Project Manhattan", Paths.get("c:\\temp\\manhattan"));
    new DirectoryWatcher(proj1, proj2).processEvents();
  }

}
