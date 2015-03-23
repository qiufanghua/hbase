/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.procedure2;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.util.Threads;
import org.apache.hadoop.hbase.procedure2.store.ProcedureStore;
import org.apache.hadoop.hbase.procedure2.store.wal.WALProcedureStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProcedureTestingUtility {
  private static final Log LOG = LogFactory.getLog(ProcedureTestingUtility.class);

  private ProcedureTestingUtility() {
  }

  public static ProcedureStore createStore(final Configuration conf, final FileSystem fs,
      final Path baseDir) throws IOException {
    return createWalStore(conf, fs, baseDir);
  }

  public static WALProcedureStore createWalStore(final Configuration conf, final FileSystem fs,
      final Path logDir) throws IOException {
    return new WALProcedureStore(conf, fs, logDir, new WALProcedureStore.LeaseRecovery() {
      @Override
      public void recoverFileLease(FileSystem fs, Path path) throws IOException {
        // no-op
      }
    });
  }

  public static <TEnv> void restart(ProcedureExecutor<TEnv> procExecutor)
      throws Exception {
    ProcedureStore procStore = procExecutor.getStore();
    int nthreads = procExecutor.getNumThreads();
    procExecutor.stop();
    procStore.stop();
    procExecutor.join();
    procStore.start(nthreads);
    procExecutor.start(nthreads);
  }

  public static <TEnv> void setKillBeforeStoreUpdate(ProcedureExecutor<TEnv> procExecutor,
      boolean value) {
    if (procExecutor.testing == null) {
      procExecutor.testing = new ProcedureExecutor.Testing();
    }
    procExecutor.testing.killBeforeStoreUpdate = value;
    LOG.warn("Set Kill before store update to: " + procExecutor.testing.killBeforeStoreUpdate);
  }

  public static <TEnv> void setToggleKillBeforeStoreUpdate(ProcedureExecutor<TEnv> procExecutor,
      boolean value) {
    if (procExecutor.testing == null) {
      procExecutor.testing = new ProcedureExecutor.Testing();
    }
    procExecutor.testing.toggleKillBeforeStoreUpdate = value;
  }

  public static <TEnv> void toggleKillBeforeStoreUpdate(ProcedureExecutor<TEnv> procExecutor) {
    if (procExecutor.testing == null) {
      procExecutor.testing = new ProcedureExecutor.Testing();
    }
    procExecutor.testing.killBeforeStoreUpdate = !procExecutor.testing.killBeforeStoreUpdate;
    LOG.warn("Set Kill before store update to: " + procExecutor.testing.killBeforeStoreUpdate);
  }

  public static <TEnv> long submitAndWait(ProcedureExecutor<TEnv> procExecutor, Procedure proc) {
    long procId = procExecutor.submitProcedure(proc);
    waitProcedure(procExecutor, procId);
    return procId;
  }

  public static <TEnv> void waitProcedure(ProcedureExecutor<TEnv> procExecutor, long procId) {
    while (!procExecutor.isFinished(procId) && procExecutor.isRunning()) {
      Threads.sleepWithoutInterrupt(250);
    }
  }

  public static <TEnv> void assertProcNotYetCompleted(ProcedureExecutor<TEnv> procExecutor,
      long procId) {
    assertFalse(procExecutor.isFinished(procId));
    assertEquals(null, procExecutor.getResult(procId));
  }

  public static <TEnv> void assertProcNotFailed(ProcedureExecutor<TEnv> procExecutor,
      long procId) {
    ProcedureResult result = procExecutor.getResult(procId);
    assertTrue(result != null);
    assertProcNotFailed(result);
  }

  public static void assertProcNotFailed(final ProcedureResult result) {
    Exception exception = result.getException();
    String msg = exception != null ? exception.toString() : "no exception found";
    assertFalse(msg, result.isFailed());
  }

  public static void assertIsAbortException(final ProcedureResult result) {
    LOG.info(result.getException());
    assertTrue(result.isFailed());
    assertTrue(result.getException().getCause() instanceof ProcedureAbortedException);
  }
}
