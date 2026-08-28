package com.websarva.wings.android.slevo.di

import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublisher
import com.websarva.wings.android.slevo.notification.AndroidReplyNotificationPublisher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 返信通知のAndroid実装を、共通UseCaseが利用する抽象へ束縛するHilt module。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    /** Android通知Publisherを共通の通知配信インターフェースへ束縛する。 */
    @Binds
    @Singleton
    abstract fun bindReplyNotificationPublisher(
        publisher: AndroidReplyNotificationPublisher,
    ): ReplyNotificationPublisher
}
