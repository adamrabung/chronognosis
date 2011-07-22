/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectoryWatcher {
  private final WatchService _watcher = FileSystems.getDefault().newWatchService();
  private final Map<WatchKey, Path> _watchKeyForPath = new HashMap<WatchKey, Path>();
  private final List<IPathWatcher> _watchers = new ArrayList<IPathWatcher>();

  public DirectoryWatcher(IPathWatcher watcher, IPathWatcher... moreWatchers) throws IOException {
    register(watcher);
    for (IPathWatcher w : moreWatchers) {
      register(w);
    }
  }

  private void register(IPathWatcher pathWatcher) throws IOException {
    _watchers.add(pathWatcher);
    for (Path root : pathWatcher.getRoots()) {
      if (!root.toFile().exists()) {
        throw new RuntimeException("Could not watch " + root + ": it does not exist");
      }
      if (pathWatcher.isRecursive()) {
        registerAll(root);
      }
      else {
        register(root);
      }
    }
  }

  /**
   * Register the given directory, and all its sub-directories, with the
   * WatchService.
   */
  private void registerAll(Path start) throws IOException {
    // register directory and sub-directories
    Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        register(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private void register(Path dir) throws IOException {
    WatchKey key = dir.register(_watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    _watchKeyForPath.put(key, dir);
  }

  @SuppressWarnings("unchecked")
  static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }

  /**
   * Process all events for keys queued to the watcher
   */
  void processEvents() {
    for (;;) {
      // wait for key to be signalled
      WatchKey key;
      try {
        key = _watcher.take();
      }
      catch (InterruptedException x) {
        return;
      }

      Path dir = _watchKeyForPath.get(key);
      if (dir == null) {
        System.err.println("WatchKey not recognized!!");
        continue;
      }

      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();

        // TBD - provide example of how OVERFLOW event is handled
        if (kind == OVERFLOW) {
          continue;
        }

        // Context for directory entry event is the file name of entry
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = dir.resolve(name);

        boolean anyRelatedWatchersRecursive = false;
        for (IPathWatcher relatedWatcher : getRelatedWatchers(child)) {
          anyRelatedWatchersRecursive = anyRelatedWatchersRecursive || relatedWatcher.isRecursive();
          // print out event
          relatedWatcher.onPathEvent(ev);
        }

        // if directory is created, and watching recursively, then
        // register it and its sub-directories
        if (anyRelatedWatchersRecursive && (kind == ENTRY_CREATE)) {
          try {
            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
              registerAll(child);
            }
          }
          catch (IOException x) {
            // ignore to keep sample readbale
          }
        }
      }

      // reset key and remove from set if directory no longer accessible
      boolean valid = key.reset();
      if (!valid) {
        _watchKeyForPath.remove(key);

        // all directories are inaccessible
        if (_watchKeyForPath.isEmpty()) {
          break;
        }
      }
    }
  }

  private Iterable<IPathWatcher> getRelatedWatchers(Path p) {
    Set<IPathWatcher> related = new HashSet<IPathWatcher>();
    for (IPathWatcher watcher : _watchers) {
      for (Path root : watcher.getRoots()) {
        if (p.startsWith(root)) {
          related.add(watcher);
        }
      }
    }
    return related;
  }
}