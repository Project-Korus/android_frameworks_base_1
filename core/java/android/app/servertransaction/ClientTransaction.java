/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app.servertransaction;

import android.app.IApplicationThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

/**
 * A container that holds a sequence of messages, which may be sent to a client.
 * This includes a list of callbacks and a final lifecycle state.
 *
 * @see com.android.server.am.ClientLifecycleManager
 * @see ClientTransactionItem
 * @see ActivityLifecycleItem
 * @hide
 */
public class ClientTransaction implements Parcelable {

    /** A list of individual callbacks to a client. */
    private List<ClientTransactionItem> mActivityCallbacks;

    /**
     * Final lifecycle state in which the client activity should be after the transaction is
     * executed.
     */
    private ActivityLifecycleItem mLifecycleStateRequest;

    /** Target client. */
    private IApplicationThread mClient;

    /** Target client activity. Might be null if the entire transaction is targeting an app. */
    private IBinder mActivityToken;

    public ClientTransaction(IApplicationThread client, IBinder activityToken) {
        mClient = client;
        mActivityToken = activityToken;
    }

    /**
     * Add a message to the end of the sequence of callbacks.
     * @param activityCallback A single message that can contain a lifecycle request/callback.
     */
    public void addCallback(ClientTransactionItem activityCallback) {
        if (mActivityCallbacks == null) {
            mActivityCallbacks = new ArrayList<>();
        }
        mActivityCallbacks.add(activityCallback);
    }

    /**
     * Set the lifecycle state in which the client should be after executing the transaction.
     * @param stateRequest A lifecycle request initialized with right parameters.
     */
    public void setLifecycleStateRequest(ActivityLifecycleItem stateRequest) {
        mLifecycleStateRequest = stateRequest;
    }

    /**
     * Do what needs to be done while the transaction is being scheduled on the client side.
     * @param clientTransactionHandler Handler on the client side that will executed all operations
     *                                 requested by transaction items.
     */
    public void prepare(android.app.ClientTransactionHandler clientTransactionHandler) {
        if (mActivityCallbacks != null) {
            final int size = mActivityCallbacks.size();
            for (int i = 0; i < size; ++i) {
                mActivityCallbacks.get(i).prepare(clientTransactionHandler, mActivityToken);
            }
        }
        if (mLifecycleStateRequest != null) {
            mLifecycleStateRequest.prepare(clientTransactionHandler, mActivityToken);
        }
    }

    /**
     * Execute the transaction.
     * @param clientTransactionHandler Handler on the client side that will execute all operations
     *                                 requested by transaction items.
     */
    public void execute(android.app.ClientTransactionHandler clientTransactionHandler) {
        if (mActivityCallbacks != null) {
            final int size = mActivityCallbacks.size();
            for (int i = 0; i < size; ++i) {
                mActivityCallbacks.get(i).execute(clientTransactionHandler, mActivityToken);
            }
        }
        if (mLifecycleStateRequest != null) {
            mLifecycleStateRequest.execute(clientTransactionHandler, mActivityToken);
        }
    }

    /**
     * Schedule the transaction after it was initialized. It will be send to client and all its
     * individual parts will be applied in the following sequence:
     * 1. The client calls {@link #prepare()}, which triggers all work that needs to be done before
     *    actually scheduling the transaction for callbacks and lifecycle state request.
     * 2. The transaction message is scheduled.
     * 3. The client calls {@link #execute()}, which executes all callbacks and necessary lifecycle
     *    transitions.
     */
    public void schedule() throws RemoteException {
        mClient.scheduleTransaction(this);
    }


    // Parcelable implementation

    /** Write to Parcel. */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(mClient.asBinder());
        final boolean writeActivityToken = mActivityToken != null;
        dest.writeBoolean(writeActivityToken);
        if (writeActivityToken) {
            dest.writeStrongBinder(mActivityToken);
        }
        dest.writeParcelable(mLifecycleStateRequest, flags);
        final boolean writeActivityCallbacks = mActivityCallbacks != null;
        dest.writeBoolean(writeActivityCallbacks);
        if (writeActivityCallbacks) {
            dest.writeParcelableList(mActivityCallbacks, flags);
        }
    }

    /** Read from Parcel. */
    private ClientTransaction(Parcel in) {
        mClient = (IApplicationThread) in.readStrongBinder();
        final boolean readActivityToken = in.readBoolean();
        if (readActivityToken) {
            mActivityToken = in.readStrongBinder();
        }
        mLifecycleStateRequest = in.readParcelable(getClass().getClassLoader());
        final boolean readActivityCallbacks = in.readBoolean();
        if (readActivityCallbacks) {
            mActivityCallbacks = new ArrayList<>();
            in.readParcelableList(mActivityCallbacks, getClass().getClassLoader());
        }
    }

    public static final Creator<ClientTransaction> CREATOR =
            new Creator<ClientTransaction>() {
        public ClientTransaction createFromParcel(Parcel in) {
            return new ClientTransaction(in);
        }

        public ClientTransaction[] newArray(int size) {
            return new ClientTransaction[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
