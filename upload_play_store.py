import argparse
import sys
from google.oauth2.service_account import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload


def main():
    parser = argparse.ArgumentParser(description="Upload AAB to Google Play Store")
    parser.add_argument('--package_name', required=True, help='The package name of the app')
    parser.add_argument('--aab_path', required=True, help='Path to the .aab file')
    parser.add_argument('--service_account_json', required=True, help='Path to the service account JSON file')
    parser.add_argument('--track', default='production', help='The track to release on (e.g., production, beta, alpha, internal)')
    parser.add_argument('--release_notes', default='', help='Structured release notes for Spanish (es-419 and es-ES) using multiline bullet points (- Item)')
    parser.add_argument('--release_notes_en', default='', help='Structured release notes for English (en-US) using multiline bullet points (- Item)')
    parser.add_argument('--release_notes_zh', default='', help='Structured release notes for Chinese Simplified (zh-CN) using multiline bullet points (- Item)')
    parser.add_argument('--release_notes_zh_tw', default='', help='Structured release notes for Chinese Traditional (zh-TW) using multiline bullet points (- Item)')
    parser.add_argument('--status', default='completed', choices=['draft', 'inProgress', 'halted', 'completed'], help='Release status for the Play track (default: completed)')
    parser.add_argument('--dry_run', action='store_true', help='Validate upload and track assignment without committing')

    args = parser.parse_args()

    scopes = ['https://www.googleapis.com/auth/androidpublisher']
    credentials = Credentials.from_service_account_file(args.service_account_json, scopes=scopes)
    service = build('androidpublisher', 'v3', credentials=credentials)

    print(f"Creating an edit for package {args.package_name}...")
    edit_request = service.edits().insert(body={}, packageName=args.package_name)
    result = edit_request.execute()
    edit_id = result['id']

    try:
        print(f"Uploading AAB {args.aab_path}...")
        media = MediaFileUpload(args.aab_path, mimetype='application/octet-stream', resumable=True)
        bundle_response = service.edits().bundles().upload(
            editId=edit_id,
            packageName=args.package_name,
            media_body=media
        ).execute()

        version_code = bundle_response['versionCode']
        print(f"Successfully uploaded AAB with version code: {version_code}")

        release_notes_list = []
        if args.release_notes:
            release_notes_list.append({'language': 'es-419', 'text': args.release_notes})
            release_notes_list.append({'language': 'es-ES', 'text': args.release_notes})

        en_notes = args.release_notes_en if args.release_notes_en else args.release_notes
        if en_notes:
            release_notes_list.append({'language': 'en-US', 'text': en_notes})

        if args.release_notes_zh:
            release_notes_list.append({'language': 'zh-CN', 'text': args.release_notes_zh})
            zh_tw = args.release_notes_zh_tw if args.release_notes_zh_tw else args.release_notes_zh
            release_notes_list.append({'language': 'zh-TW', 'text': zh_tw})

        new_release = {
            'name': f"Release {version_code}",
            'versionCodes': [version_code],
            'status': args.status,
            'releaseNotes': release_notes_list
        }
        releases = [new_release]

        # Un borrador debe convivir con la release publicada actual. Si se
        # reemplazara la lista completa, la track podría quedarse sin release
        # activa mientras el borrador no se despliega.
        if args.status == 'draft':
            try:
                current_track = service.edits().tracks().get(
                    editId=edit_id,
                    packageName=args.package_name,
                    track=args.track
                ).execute()
                existing_releases = [
                    release for release in current_track.get('releases', [])
                    if release.get('status') != 'draft'
                ]
            except Exception:
                existing_releases = []
            releases = existing_releases + releases

        track_release = {
            'track': args.track,
            'releases': releases
        }

        print(f"Assigning AAB to track '{args.track}' with status '{args.status}'...")
        service.edits().tracks().update(
            editId=edit_id,
            packageName=args.package_name,
            track=args.track,
            body=track_release
        ).execute()

        if args.dry_run:
            print("Validating the edit (dry-run mode)...")
            service.edits().validate(editId=edit_id, packageName=args.package_name).execute()
            print("Validation successful! Deleting edit to avoid committing.")
            service.edits().delete(editId=edit_id, packageName=args.package_name).execute()
            print("Dry-run completed successfully.")
            return

        print("Committing the edit...")
        service.edits().commit(editId=edit_id, packageName=args.package_name).execute()
        if args.status == 'draft':
            print("Draft release successfully created on Google Play!")
        else:
            print("Release successfully published to Google Play!")

    except Exception as e:
        print(f"An error occurred: {e}")
        print("Reverting the edit...")
        service.edits().delete(editId=edit_id, packageName=args.package_name).execute()
        sys.exit(1)


if __name__ == '__main__':
    main()
