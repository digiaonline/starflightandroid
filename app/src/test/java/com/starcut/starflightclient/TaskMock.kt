package com.starcut.starflightclient

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.iid.InstanceIdResult
import java.util.concurrent.Executor

class TaskMock : Task<InstanceIdResult>() {

    override fun getResult(): InstanceIdResult? {
        return mockResult
    }

    var mockResult : InstanceIdResult? = null

    override fun isComplete(): Boolean {
        return true
    }

    override fun getException(): Exception? {
        return null
    }

    override fun addOnFailureListener(p0: OnFailureListener): Task<InstanceIdResult> {
        throw Exception("Not implemented")
    }

    override fun addOnFailureListener(p0: Executor, p1: OnFailureListener): Task<InstanceIdResult> {
        throw Exception("Not implemented")
    }

    override fun addOnFailureListener(p0: Activity, p1: OnFailureListener): Task<InstanceIdResult> {
        throw Exception("Not implemented")
    }

    override fun <X : Throwable?> getResult(p0: Class<X>): InstanceIdResult? {
        throw Exception("Not implemented")
    }

    override fun addOnSuccessListener(p0: OnSuccessListener<in InstanceIdResult>): Task<InstanceIdResult> {
        p0.onSuccess(mockResult)
        return this
    }

    override fun addOnSuccessListener(
        p0: Executor,
        p1: OnSuccessListener<in InstanceIdResult>
    ): Task<InstanceIdResult> {
        throw Exception("Not implemented")
    }

    override fun addOnSuccessListener(
        p0: Activity,
        p1: OnSuccessListener<in InstanceIdResult>
    ): Task<InstanceIdResult> {
        throw Exception("Not implemented")
    }

    override fun isSuccessful(): Boolean {
        return true
    }

    override fun isCanceled(): Boolean {
        return false
    }
}