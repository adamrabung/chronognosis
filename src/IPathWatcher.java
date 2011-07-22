import java.nio.file.Path;
import java.nio.file.WatchEvent;

public interface IPathWatcher {
  Iterable<Path> getRoots();

  boolean isRecursive();

  void onPathEvent(WatchEvent<Path> event);
}
