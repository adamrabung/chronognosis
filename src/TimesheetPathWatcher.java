import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;

public class TimesheetPathWatcher implements IPathWatcher {
  private final ITimesheetDatabase _timesheetDatabase;
  private final String _projectName;
  private final List<Path> _roots;

  public TimesheetPathWatcher(ITimesheetDatabase database, String projectName, Path watchRoot, Path... moreRoots) {
    _timesheetDatabase = database;
    _projectName = projectName;
    _roots = new ArrayList<Path>();
    _roots.add(watchRoot);
    for (Path root : moreRoots) {
      _roots.add(root);
    }
  }

  @Override
  public Iterable<Path> getRoots() {
    return _roots;
  }

  @Override
  public boolean isRecursive() {
    return true;
  }

  @Override
  public void onPathEvent(WatchEvent<Path> event) {
    _timesheetDatabase.onProjectFileAccessed(_projectName);
  }
}
