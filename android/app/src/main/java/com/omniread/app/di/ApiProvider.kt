package com.omniread.app.di

import com.omniread.app.data.api.OmniReadApi

object ApiProvider {
    lateinit var api: OmniReadApi
        internal set
}
