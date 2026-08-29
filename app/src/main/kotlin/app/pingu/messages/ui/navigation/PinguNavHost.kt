package app.pingu.messages.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIntoContainer
import androidx.compose.animation.slideOutOfContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.pingu.messages.di.AppContainer
import app.pingu.messages.ui.PinguViewModels
import app.pingu.messages.ui.screens.blocked.BlockedScreen
import app.pingu.messages.ui.screens.blocked.BlockedViewModel
import app.pingu.messages.ui.screens.conversation.ConversationScreen
import app.pingu.messages.ui.screens.conversation.ConversationViewModel
import app.pingu.messages.ui.screens.conversations.ConversationsScreen
import app.pingu.messages.ui.screens.conversations.ConversationsViewModel
import app.pingu.messages.ui.screens.gallery.ConversationMediaScreen
import app.pingu.messages.ui.screens.gallery.ConversationMediaViewModel
import app.pingu.messages.ui.screens.media.MediaViewerScreen
import app.pingu.messages.ui.screens.media.MediaViewerViewModel
import app.pingu.messages.ui.screens.newmessage.NewMessageScreen
import app.pingu.messages.ui.screens.newmessage.NewMessageViewModel
import app.pingu.messages.ui.screens.onboarding.OnboardingScreen
import app.pingu.messages.ui.screens.onboarding.OnboardingViewModel
import app.pingu.messages.ui.screens.scheduled.ScheduledScreen
import app.pingu.messages.ui.screens.scheduled.ScheduledViewModel
import app.pingu.messages.ui.screens.search.SearchScreen
import app.pingu.messages.ui.screens.search.SearchViewModel
import app.pingu.messages.ui.screens.settings.AboutScreen
import app.pingu.messages.ui.screens.settings.SettingsScreen
import app.pingu.messages.ui.screens.settings.SettingsViewModel

/**
 * The navigation graph.
 *
 * Transitions are a short horizontal slide for forward navigation and its reverse for back, which
 * is the platform convention and reads as depth without being decorative. Nothing animates for
 * longer than 250ms; a messaging app is used in short bursts and waiting for an animation is the
 * fastest way to make one feel slow.
 */
@Composable
fun PinguNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String,
    onRequestDefaultSmsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MILLIS),
            ) + fadeIn(tween(TRANSITION_MILLIS))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_MILLIS),
            ) + fadeOut(tween(TRANSITION_MILLIS))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MILLIS),
            ) + fadeIn(tween(TRANSITION_MILLIS))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_MILLIS),
            ) + fadeOut(tween(TRANSITION_MILLIS))
        },
    ) {
        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel =
                viewModel(factory = PinguViewModels.onboarding(container))
            OnboardingScreen(
                viewModel = viewModel,
                onRequestDefaultSmsApp = onRequestDefaultSmsApp,
                onFinished = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.CONVERSATIONS) {
            val viewModel: ConversationsViewModel =
                viewModel(factory = PinguViewModels.conversations(container))
            ConversationsScreen(
                viewModel = viewModel,
                onOpenConversation = { navController.navigate(Routes.conversation(it)) },
                onCompose = { navController.navigate(Routes.newMessage()) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onScheduled = { navController.navigate(Routes.SCHEDULED) },
                onBlocked = { navController.navigate(Routes.BLOCKED) },
                onRequestDefaultSmsApp = onRequestDefaultSmsApp,
            )
        }

        composable(
            route = Routes.CONVERSATION,
            arguments = listOf(
                navArgument(Routes.ARG_THREAD_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_MESSAGE_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getLong(Routes.ARG_THREAD_ID) ?: 0L
            val messageId = entry.arguments?.getLong(Routes.ARG_MESSAGE_ID) ?: 0L
            val viewModel: ConversationViewModel =
                viewModel(factory = PinguViewModels.conversation(container, threadId))

            androidx.compose.runtime.LaunchedEffect(messageId) {
                if (messageId > 0) viewModel.jumpToMessage(messageId)
            }

            ConversationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMedia = { openedMessageId, uri ->
                    navController.navigate(Routes.mediaViewer(threadId, openedMessageId, uri))
                },
                onForward = { ids ->
                    navController.navigate(Routes.newMessage(forwardIds = ids))
                },
                onOpenConversationMedia = {
                    navController.navigate(Routes.conversationMedia(it))
                },
            )
        }

        composable(
            route = Routes.NEW_MESSAGE,
            arguments = listOf(
                navArgument(Routes.ARG_ADDRESS) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_BODY) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_FORWARD_IDS) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val address = entry.arguments?.getString(Routes.ARG_ADDRESS).orEmpty()
            val forwardIds = Routes.parseForwardIds(entry.arguments?.getString(Routes.ARG_FORWARD_IDS))
            val viewModel: NewMessageViewModel =
                viewModel(factory = PinguViewModels.newMessage(container, forwardIds))

            androidx.compose.runtime.LaunchedEffect(address) {
                if (address.isNotBlank()) viewModel.addRecipient(address)
            }

            NewMessageScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { threadId ->
                    navController.navigate(Routes.conversation(threadId)) {
                        popUpTo(Routes.CONVERSATIONS)
                    }
                },
            )
        }

        composable(Routes.SEARCH) {
            val viewModel: SearchViewModel = viewModel(factory = PinguViewModels.search(container))
            SearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { navController.navigate(Routes.conversation(it)) },
                onOpenMessage = { threadId, messageId ->
                    navController.navigate(Routes.conversation(threadId, messageId))
                },
                onStartConversation = { address ->
                    navController.navigate(Routes.newMessage(address = address))
                },
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel =
                viewModel(factory = PinguViewModels.settings(container))
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenBlocked = { navController.navigate(Routes.BLOCKED) },
                onOpenScheduled = { navController.navigate(Routes.SCHEDULED) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onRequestDefaultSmsApp = onRequestDefaultSmsApp,
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BLOCKED) {
            val viewModel: BlockedViewModel = viewModel(factory = PinguViewModels.blocked(container))
            BlockedScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { navController.navigate(Routes.conversation(it)) },
            )
        }

        composable(Routes.SCHEDULED) {
            val viewModel: ScheduledViewModel =
                viewModel(factory = PinguViewModels.scheduled(container))
            ScheduledScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenConversation = { navController.navigate(Routes.conversation(it)) },
            )
        }

        composable(
            route = Routes.MEDIA_VIEWER,
            arguments = listOf(
                navArgument(Routes.ARG_THREAD_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_MESSAGE_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_URI) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val threadId = entry.arguments?.getLong(Routes.ARG_THREAD_ID) ?: 0L
            val messageId = entry.arguments?.getLong(Routes.ARG_MESSAGE_ID) ?: 0L
            val uri = entry.arguments?.getString(Routes.ARG_URI).orEmpty()
            val viewModel: MediaViewerViewModel = viewModel(
                factory = PinguViewModels.mediaViewer(container, threadId, messageId, uri),
            )
            MediaViewerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CONVERSATION_MEDIA,
            arguments = listOf(navArgument(Routes.ARG_THREAD_ID) { type = NavType.LongType }),
        ) { entry ->
            val threadId = entry.arguments?.getLong(Routes.ARG_THREAD_ID) ?: 0L
            val viewModel: ConversationMediaViewModel = viewModel(
                factory = PinguViewModels.conversationMedia(container, threadId),
            )
            ConversationMediaScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMedia = { messageId, uri ->
                    navController.navigate(Routes.mediaViewer(threadId, messageId, uri))
                },
            )
        }
    }
}

private const val TRANSITION_MILLIS = 220
