# Google Reviews

Status: proposed link-only feature. [Index](README.md).

## Current source

[Restaurant](../backend/src/main/java/com/atelier/media/model/Restaurant.java), [StudioSettingsUpdateDto](../backend/src/main/java/com/atelier/media/dto/StudioSettingsUpdateDto.java) and [SocialBar](../frontend/src/components/guest/SocialBar.jsx) include Maps/contact settings. An existing directions link is not a review destination, fetched Google rating or posted-review integration. No dedicated review setting/CTA was found.

## Proposed configuration and flow

Add separate review destination and owner enable switch, restricted by effective reviews entitlement. Restaurant obtains its legitimate review link from Google Business Profile, pastes it, previews the destination and saves it. Guest sees “Review us on Google” in a secondary area with a clear external-link behavior. Do not imitate the review editor, collect a rating while presenting it as Google, invent stars/counts or selectively ask only happy guests.

Google documents review-request links/QR and warns against incentives; users need a Google account to post. [Google Business Profile guidance](https://support.google.com/business/answer/3474122?hl=en) supports this external-link approach. A click cannot establish that a review was submitted.

Server accepts only HTTPS with exact approved Google review/Maps hosts and known supported path/query shapes. Parse URLs, reject credentials/ports/control characters, deceptive suffixes and unsupported schemes. Normalize a reviewed shape rather than accepting any URL containing “google”. The actual accepted link formats must be tested against real Google Business Profile exports at implementation time; short URLs/redirects must not trigger arbitrary server fetches. Prefer no server dereferencing. Exact allowlist and the preview UX require approval after collecting legitimate examples.

## Dependencies, decisions and security

Depends on entitlement/settings/public DTO additions and publication cache invalidation. Preserve existing directions URL independently. Invalid/empty links keep the guest action hidden; disabled entitlement prevents saving/enabling through the API. External anchors use safe rel/referrer behavior and no table tokens in the destination. Do not guarantee venue ownership solely from an allowed Google host; owner confirmation and an admin review path can address misconfiguration.

Live ratings/reviews display is a separate official API integration requiring an approved provider/account/API key, attribution/caching policy review, quota/cost analysis and security design. None is selected or activated here. No scraping or automatic solicitation system.

## Acceptance and test scenarios

Test allowed legitimate links, deceptive hosts, javascript/data URLs, userinfo, encoded redirects, overlong input and expired/disabled capabilities. Verify tenant A cannot change B’s review settings; save/reload/cache behavior matches guest display. Test keyboard/mobile placement, Google signed-in/out behavior and unavailable destination guidance. Track only REVIEW_LINK_OPENED if analytics approved; never “review completed”. Confirm review prompts are neutral and not conditioned on a positive internal rating or an incentive.
