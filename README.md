# Aether

Aether is an android native app intended to be used with a pre-existing hydrus installation.

My idea is to bring the convenience of a processed or in process hydrus gallery to handheld (android only for now...) devices.

## Media
- Supports mostly anything that can be normally played on an android device, be it your jpeg, png, gifs, mp4, webm, webp...
- Built in hide PSD, PDF and ZIP files
- Inbox, archive and delete to selected files
- Archive / delete processing of the searched context
- Simple duplicate processing

## Tags
- Add / remove tags to both individual or multiple selected files
- Search and manage tags
- Tag Wiki

With bigger galleries containing imports from manual, and multiple other sites, remembering what some tags meant becomes quite a difficult task so, I've added a Tag Wiki, like your normal boorus, this allows you to write a description (with support for referencing other tags from the system), check some example files from your client, add aliases that you can also search and add references to external sites.

## Privacy

In the same fashion as hydrus, this app is made with privacy in mind, so I have included a privacy toggle in the settings menu, to keep whatever you write private to your self and not used for training data on your keyboard maker.

The app will not store data using android's restorable structure, that means if you uninstall the app, everything gets wiped, so remember to make backups using the backup function. 
Which, speaking of, with the privacy thing in mind, I thought the best idea was to keep the app backup inside of hydrus, as a zipped file. **Bear in mind that eventhough the app contains a auto backup function, I would still strongly suggest doing ocasional manual backups.**

As far as I have tested, the app is capable of working with both local (within the same network) and remote (cloudflared/vpn) hydrus instances. 

## Getting started
For your convenience, you can find an up to date, unsigned and precompiled android apk in the releases section.

Check [here](https://github.com/asadtoast/aether/wiki) for details on how to use all the available options of the app.
